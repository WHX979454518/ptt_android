package com.xianzhitech.ptt.service.impl

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.Looper
import android.os.Vibrator
import android.support.annotation.RawRes
import android.support.v4.content.LocalBroadcastManager
import android.util.SparseIntArray
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.getActiveNetwork
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.onSingleValue
import com.xianzhitech.ptt.ext.subscribeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toBase64
import com.xianzhitech.ptt.ext.toMD5
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.ext.toStringIterable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.CreateRoomFromGroup
import com.xianzhitech.ptt.service.CreateRoomFromUser
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.UserToken
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.service.Service
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.Transport
import org.json.JSONArray
import org.json.JSONObject
import rx.Completable
import rx.Observable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.observers.SafeSubscriber
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.Subscriptions
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SignalServiceImpl(private val appContext: Context,
                        private val signalServerEndpoint : String,
                        private val preference: Preference,
                        private val userRepository : UserRepository,
                        private val groupRepository : GroupRepository,
                        private val contactRepository : ContactRepository,
                        private val roomRepository : RoomRepository,
                        private val talkEngineProvider: TalkEngineProvider) : SignalService {
    var loginSubscription : Subscription? = null
    var roomSubscription: CompositeSubscription? = null
    var requestMicSubscription : Subscription? = null
    var currentTalkEngine: TalkEngine? = null

    private val soundPool: Pair<SoundPool, SparseIntArray> by lazy {
        Pair(SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0), SparseIntArray()).apply {
            second.put(R.raw.incoming, first.load(appContext, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(appContext, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(appContext, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(appContext, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(appContext, R.raw.pttup_offline, 0))
        }
    }
    private val soundPlayExecutor by lazy { Executors.newSingleThreadExecutor() }
    private var audioManager: AudioManager
    private var vibrator : Vibrator? = null

    private var socketSubject = BehaviorSubject.create<Socket>()

    override var roomState = BehaviorSubject.create<RoomState>(RoomState())
    override var loginState = BehaviorSubject.create<LoginState>(LoginState())

    init {
        vibrator = (appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let { if (it.hasVibrator()) it else null }
        audioManager = (appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        val loginStateValue = loginState.value
        if (loginStateValue.currentUserID == null && loginStateValue.status == LoginStatus.IDLE) {
            val currentUserSessionToken = preference.userSessionToken as? ExplicitUserToken
            val lastUserId = preference.lastLoginUserId
            if (currentUserSessionToken != null && lastUserId != null) {
                loginState.onNext(LoginState(currentUserID = lastUserId, status = LoginStatus.LOGIN_IN_PROGRESS))
                doLogin(currentUserSessionToken).subscribeSimple()
            }
        }

        if (BuildConfig.DEBUG) {
            loginState.subscribe { logd("Login state: $it") }
            roomState.subscribe { logd("Room state: $it") }
        }

        // Record last active speaker to database
        roomState.distinctUntilChanged { it.currentRoomActiveSpeakerID }
                .subscribeSimple {
                    if (it.currentRoomID != null && it.currentRoomActiveSpeakerID != null) {
                        roomRepository.updateLastRoomActiveUser(it.currentRoomID, Date(), it.currentRoomActiveSpeakerID).execAsync().subscribeSimple()
                    }
                }
    }

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    override fun logout() = Observable.defer { doLogout().toObservable() }
            .subscribeOnMainThread()
            .doOnNext { appContext.stopService(Intent(appContext, Service::class.java)) }

    private fun doLogout() {
        loginState.value.currentUserID?.let {
            preference.userSessionToken = null
            onUserLogout(it)
        }

        loginState.onNext(LoginState())

        socketSubject.value?.close()
        socketSubject = BehaviorSubject.create()

        loginSubscription = loginSubscription?.let { it.unsubscribe(); null }
    }

    override fun login(username: String, password: String) = Observable.defer {
        loginState.value.currentUserID?.let {
            doLogout()
        }

        doLogin(ExplicitUserToken(username, password))
    }.subscribeOnMainThread()

    private fun doLogin(userToken : ExplicitUserToken) = Observable.create<Unit> { subscriber ->
        val newSocket = IO.socket(signalServerEndpoint, IO.Options().apply {
            reconnection = true
            forceNew = true
        })

        // Process headers
        newSocket.io().on(Manager.EVENT_TRANSPORT, {
            (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                val token = (preference.userSessionToken as? ExplicitUserToken) ?: userToken

                (it[0] as MutableMap<String, List<String>>)["Authorization"] =
                        listOf("Basic ${(token.userId + ':' + token.password.toMD5()).toBase64()}");
            })
        })

        // Monitor user login
        loginSubscription = CompositeSubscription().apply {
            add(newSocket.io().receiveEvent<Any>(Manager.EVENT_RECONNECT)
                    .subscribe {
                        logd("Reconnecting to server...")
                        socketSubject.onNext(newSocket)
                    })

            add(newSocket.receiveEventIgnoringArguments(EVENT_SERVER_USER_LOGIN_FAILED)
                    .observeOnMainThread()
                    .subscribeSimple {
                        subscriber.onError(StaticUserException(R.string.error_login_failed))
                        doLogout()
                    })

            add(newSocket.io().receiveEventIgnoringArguments(Manager.EVENT_CONNECT_ERROR)
                    .observeOnMainThread()
                    .subscribe {
                        loginState.onNext(loginState.value.copy(status = LoginStatus.OFFLINE))
                    })

            add(newSocket.receiveEvent<JSONObject>(EVENT_SERVER_USER_LOGON)
                    .map { response ->
                        logd("Login response: $response")

                        /**
                         * Response: { userObject }
                         */
                        val user = UserObject(response)

                        if (preference.lastLoginUserId != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preference.lastLoginUserId = user.id
                            roomRepository.clearRooms().exec()
                        }

                        userRepository.saveUsers(listOf(user)).exec()

                        user
                    }
                    .doOnNext {
                        // Do sync contacts
                        /**
                         * Request:
                         * {
                         *      enterMemberVersion: 1,
                         *      enterGroupVersion: 1
                         * }
                         *
                         * Response:
                         * {
                         *       enterpriseMembers: {
                         *           version: 1,
                         *           reset: true,
                         *           add: [user objects]
                         *       },
                         *       enterpriseGroups: {
                         *           version: 1,
                         *           reset: true,
                         *           add:  [group objects]
                         *       }
                         *   }
                         */
                        logd("Requesting syncing contacts")
                        newSocket.sendEvent<JSONObject>(EVENT_CLIENT_SYNC_CONTACTS, JSONObject().put("enterMemberVersion", 1).put("enterGroupVersion", 1))
                                .subscribeSimple { response ->
                                    logd("Received sync result")

                                    contactRepository.replaceAllContacts(
                                            response.getJSONObject("enterpriseMembers").getJSONArray("add").transform { UserObject(it as JSONObject) },
                                            response.getJSONObject("enterpriseGroups").getJSONArray("add").transform { GroupObject(it as JSONObject) }
                                    ).exec()
                                }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<User>() {
                        override fun onError(e: Throwable) {
                            subscriber.onError(e)
                            loginState.onNext(loginState.value.copy(status = LoginStatus.IDLE))
                        }

                        override fun onNext(t: User) {
                            val newLoginState = LoginState(LoginStatus.LOGGED_IN, t.id)
                            preference.userSessionToken = userToken
                            loginState.onNext(newLoginState)
                            subscriber.onNext(null)
                            subscriber.onCompleted()

                            onUserLogon(t)
                        }
                    }))

            add(newSocket.receiveEvent<JSONObject>(EVENT_SERVER_INVITE_TO_JOIN)
                    .map { response ->
                        /**
                         * Response:
                         *  {
                         *    roomInfo : { roomObject, members: [user IDs] },
                         *    activeMembers: [active user IDs],
                         *    speaker: "speaker ID"
                         *  }
                         */
                        val room = RoomObject(response.getJSONObject("roomInfo"))
                        roomRepository.saveRooms(listOf(room)).exec()
                        room
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Room>() {
                        override fun onNext(t: Room) {
                            onInviteToJoin(t)
                        }
                    }))

            add(newSocket.receiveEventIgnoringArguments(EVENT_SERVER_USER_KICK_OUT)
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Any>() {
                        override fun onNext(t: Any) {
                            onUserKickedOut()
                        }
                    }))

            // This must be in the end!
            add(appContext.getActiveNetwork()
                    .distinctUntilChanged()
                    .observeOnMainThread()
                    .subscribe {
                        logd("New active network: $it")
                        newSocket.disconnect()
                        if (it != null && it.isConnected) {
                            loginState.onNext(loginState.value.copy(status = LoginStatus.LOGIN_IN_PROGRESS))
                            socketSubject.onNext(newSocket.connect())
                        }
                        else {
                            // Dispose talk engine while connection is lost
                            currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
                            roomState.value?.let {
                                if (it.status != RoomStatus.IDLE) {
                                    roomState.onNext(it.copy(status = RoomStatus.OFFLINE))
                                }
                            }

                            loginState.value?.let {
                                if (it.status != LoginStatus.IDLE) {
                                    loginState.onNext(it.copy(status = LoginStatus.OFFLINE))
                                }
                            }
                        }
                    })

        }

    }
            .subscribeOnMainThread()
            .doOnSubscribe {
                val name = appContext.startService(Intent(appContext, Service::class.java))
                logd("Starting service $name")
            }

    private fun onUserKickedOut() {
        if (loginState.value.currentUserID != null) {
            logout().subscribe(GlobalSubscriber())
            appContext.startActivity(Intent(appContext, KickOutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    private fun onUserLogout(user: String) {
        logd("User $user has logged out")
//        stopSelf()
    }

    private fun onUserLogon(user : User) {
        logd("User $user has logged on")
//        startService(Intent(this, javaClass))
    }

    private fun onInviteToJoin(room: Room) {
        logd("Received invite to join room $room")

        LocalBroadcastManager.getInstance(appContext)
                .sendBroadcast(Intent(SignalService.ACTION_INVITE_TO_JOIN)
                        .putExtra(SignalService.EXTRA_INVITE, RoomInvitationImpl(room.id, room.ownerId)))
    }


    private fun onRoomJoined(roomId: String, talkEngineProperties : Map<String, Any?>) {
        //roomSubscription?.add()
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        audioManager.isSpeakerphoneOn = true
        createTalkEngine(roomId, talkEngineProperties, false)
    }

    private fun createTalkEngine(roomId : String, talkEngineProperties: Map<String, Any?>, applyCurrentState : Boolean) {
        currentTalkEngine?.let { it.dispose() }
        currentTalkEngine = talkEngineProvider.createEngine().apply {
            logd("Connecting to talk engine")
            connect(roomId, talkEngineProperties)

            if (applyCurrentState) {
                val roomState = peekRoomState()
                val userState = peekLoginState()
                if (roomState.status == RoomStatus.ACTIVE && roomState.currentRoomActiveSpeakerID == userState.currentUserID) {
                    startSend()
                }
                else {
                    stopSend()
                }
            }
        }
    }

    private fun onRoomQuited(roomId: String) {
        audioManager.isSpeakerphoneOn = false
    }

    private fun onMicActivated(isSelf: Boolean) {
        if (isSelf) {
            vibrator?.vibrate(120)
            playSound(R.raw.outgoing)
            currentTalkEngine?.startSend()
        } else {
            playSound(R.raw.incoming)
        }
    }

    private fun onMicReleased(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.pttup)
            currentTalkEngine?.stopSend()
        } else {
            playSound(R.raw.over)
        }
    }


    override fun joinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
        if (roomId == roomState.value.currentRoomID) {
            logd("Room $roomId is previously joined and active")
            subscriber.onSingleValue(Unit)
        } else {
            doJoinRoom(roomId).subscribe(object : GlobalSubscriber<Unit>() {
                override fun onNext(t: Unit) {
                    subscriber.onSingleValue(t)
                }

                override fun onError(e: Throwable) {
                    subscriber.onError(e)
                }
            })
        }
    }.subscribeOnMainThread()


    /**
     * Requests creating a room. If success, the room will be persisted to the local database.
     * Calling this method has no effect on current room state
     */
    override fun createRoom(request: CreateRoomRequest) = Observable.defer {
        logd("Creating room with $request")
        val socket = socketSubject.value
        val currentUserID = peekLoginState().currentUserID

        if (socket == null || currentUserID == null) {
            return@defer Observable.error<String>(IllegalStateException())
        }

        userRepository.getUsers(listOf(currentUserID))
                .getAsync()
                .toObservable()
                .flatMap { users ->
                    val user = users.firstOrNull()

                    // TODO: Check permission
                    if (user == null) {
                        Observable.error<String>(StaticUserException(R.string.error_no_permission))
                    } else {
                        socket.sendEvent<JSONObject>(EVENT_CLIENT_CREATE_ROOM, request.toJSON())
                                .map { response ->
                                    /**
                                     * Response:
                                     * {
                                     *      Room object,
                                     * }
                                     */
                                    val room = RoomObject(response)
                                    roomRepository.saveRooms(listOf(room)).exec()
                                    room.id
                                }
                                .toObservable()
                    }
                }
    }.subscribeOnMainThread()


    private fun doQuitCurrentRoom() {
        checkMainThread()
        val state = roomState.value
        val socket = socketSubject.value
        roomSubscription = roomSubscription?.let { it.unsubscribe(); null }

        if (state.currentRoomID != null && socket != null) {
            /**
             * Quit room:
             * Request: { roomId : "room ID" }
             * Response: Ignored
             */
            socket.sendEventIgnoringResult(EVENT_CLIENT_LEAVE_ROOM, JSONObject().put("roomId", state.currentRoomID)).subscribeSimple()
            roomState.onNext(RoomState())
            onRoomQuited(state.currentRoomID)
        }

        currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
    }


    /**
     * Actually join room. This is assumed all pre-check has been done.
     * This method has to run on main thread.
     */
    private fun doJoinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
        doQuitCurrentRoom()
        logd("Joining room $roomId")
        roomState.onNext(roomState.value.copy(status = RoomStatus.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))

        roomSubscription = CompositeSubscription().apply {
            val socketObservable = socketSubject.publish()

            // Subscribes to speaker change event
            add(socketObservable
                    .flatMap { it.receiveEvent<JSONObject>(EVENT_SERVER_SPEAKER_CHANGED) }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JSONObject>() {
                        override fun onNext(t: JSONObject) {
                            /**
                             * Speaker change response:
                             * {
                             *      roomId: "room ID",
                             *      speaker: "speaker ID"
                             * }
                             */
                            val speakerChangeRoomID = t.getString("roomId")
                            val newSpeakerID = if (t.isNull("speaker").not()) t.optString("speaker") else null
                            val currentRoomState = peekRoomState()
                            if (speakerChangeRoomID == roomId && currentRoomState.currentRoomActiveSpeakerID != newSpeakerID) {
                                roomState.onNext(currentRoomState.copy(currentRoomActiveSpeakerID = newSpeakerID,
                                        status = newSpeakerID?.let { RoomStatus.ACTIVE } ?: RoomStatus.JOINED))
                                newSpeakerID?.let { onMicActivated(false) } ?: onMicReleased(false)
                            }
                        }
                    }))

            // Subscribes to active member changes
            add(socketObservable
                    .flatMap { socket -> socket.receiveEvent<JSONObject>(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED) }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JSONObject>() {
                        override fun onNext(t: JSONObject) {
                            /**
                             * Online member change response:
                             * {
                             *      roomId: "room ID",
                             *      activeMembers: [user IDs]
                             * }
                             *
                             */
                            val currentRoomState = peekRoomState()
                            if (t.getString("roomId") == currentRoomState.currentRoomID) {
                                roomState.onNext(currentRoomState.copy(currentRoomOnlineMemberIDs = t.getJSONArray("activeMembers").toStringIterable().toSet()))
                            }
                        }
                    }))

            // Kicks off joining room event
            add(socketObservable
                    .flatMap { socket ->
                        roomState.onNext(roomState.value.copy(status = RoomStatus.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))

                        // Join the room
                        socket.sendEvent<JSONObject>(EVENT_CLIENT_JOIN_ROOM, JSONObject().put("roomId", roomId))
                                .map { response ->
                                    /**
                                     * Join room request: { roomId : "room ID" }
                                     * Response:
                                     * {
                                     *      roomInfo: { room object, members: [user IDs] },
                                     *      activeMembers: [user IDs],
                                     *      speaker: "speaker ID",
                                     *      server: {
                                     *          host: "webrtc host",
                                     *          port: 9002,
                                     *          protocol: "udp"
                                     *      }
                                     * }
                                     *
                                     */
                                    val joinRoomResponse = JoinRoomResponse(response)
                                    roomRepository.saveRooms(listOf(joinRoomResponse.room))
                                    joinRoomResponse
                                }
                                .toObservable()
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JoinRoomResponse>() {
                        override fun onError(e: Throwable) = subscriber.onError(e)
                        override fun onNext(t: JoinRoomResponse) {
                            roomState.onNext(roomState.value.copy(
                                    status = t.activeSpeakerId?.let { RoomStatus.ACTIVE } ?: RoomStatus.JOINED,
                                    currentRoomActiveSpeakerID = t.activeSpeakerId,
                                    currentRoomOnlineMemberIDs = t.activeMemberIds.toSet()
                            ))

                            val voiceServerObj = t.serverConfiguration

                            onRoomJoined(roomId, hashMapOf(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID to loginState.value.currentUserID,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_ADDRESS to voiceServerObj["host"],
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT to voiceServerObj["port"],
                                    WebRtcTalkEngine.PROPERTY_PROTOCOL to voiceServerObj["protocol"]))

                            subscriber.onSingleValue(Unit)
                        }
                    }))


            // Kicks the socket observable off
            socketObservable.connect()
        }

    }.subscribeOnMainThread()
            .doOnNext {
                appContext.startService(Intent(appContext, Service::class.java))
            }

    override fun quitRoom() = Observable.defer { doQuitCurrentRoom().toObservable() }.subscribeOnMainThread()

    override fun requestMic() = Observable.create<Unit> { subscriber ->
        val socket = socketSubject.value
        val currRoomState = peekRoomState()
        val currRoomSubscription = roomSubscription
        val currUserId = loginState.value.currentUserID

        if (socket == null || currRoomState.currentRoomID == null ||
                currRoomSubscription == null || currUserId == null) {
            subscriber.onError(IllegalStateException())
            playSound(R.raw.pttup_offline)
            return@create
        }

        val micRequest = JSONObject().put("roomId", currRoomState.currentRoomID)

        requestMicSubscription?.unsubscribe()

        requestMicSubscription = Observable.timer(200, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .flatMap {
                    roomState.onNext(roomState.value.copy(status = RoomStatus.REQUESTING_MIC))
                    socket.sendEvent<JSONObject>(EVENT_CLIENT_CONTROL_MIC, micRequest)
                            .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .toObservable()
                }
                .observeOnMainThread()
                .subscribe(SafeSubscriber(object : GlobalSubscriber<JSONObject>() {
                    override fun onError(e: Throwable) {
                        subscriber.onError(e)
                        // When error happens, always requests release mic to prevent further damage to the state
                        socket.sendEventIgnoringResult(EVENT_CLIENT_RELEASE_MIC, micRequest).subscribeSimple()
                        roomState.onNext(roomState.value.copy(status = RoomStatus.JOINED))
                        playSound(R.raw.pttup_offline)
                    }

                    override fun onNext(t: JSONObject) {
                        /**
                         * Mic request: { roomId : "roomId" }
                         * Response: { success: true, speaker: "speaker ID" }
                         */

                        val newSpeakerID = if (t.isNull("speaker").not()) t.optString("speaker") else null
                        if (t.getBoolean("success") && newSpeakerID == currUserId) {
                            if (isUnsubscribed || roomState.value.status != RoomStatus.REQUESTING_MIC) {
                                // 如果返回的时候我们已经不是请求状态了, 必须发回服务器一个RELEASE做最后保证
                                releaseMic().subscribe(GlobalSubscriber())
                            } else {
                                roomState.onNext(roomState.value.copy(currentRoomActiveSpeakerID = currUserId, status = RoomStatus.ACTIVE))
                                onMicActivated(true)
                            }
                        }

                        subscriber.onSingleValue(Unit)
                    }
                }))

        currRoomSubscription.add(requestMicSubscription)
    }.subscribeOnMainThread()


    override fun releaseMic() = Observable.defer<Unit> {
        val socket = socketSubject.value
        val currRoomState = peekRoomState()
        val currRoomSubscription = roomSubscription
        val currUserId = loginState.value.currentUserID

        requestMicSubscription?.unsubscribe()
        requestMicSubscription = null

        if (socket == null || currRoomState.currentRoomID == null ||
                currRoomSubscription == null || currUserId == null) {
            return@defer Observable.error<Unit>(IllegalStateException())
        }

        /**
         * Mic release request: { roomId : "roomId" }
         * Response: Ignored.
         */
        currRoomSubscription.add(socket.sendEventIgnoringResult(EVENT_CLIENT_RELEASE_MIC, JSONObject().put("roomId", currRoomState.currentRoomID)).subscribeSimple())

        if (currRoomState.currentRoomActiveSpeakerID == currUserId || currRoomState.currentRoomActiveSpeakerID == null) {
            roomState.onNext(currRoomState.copy(currentRoomActiveSpeakerID = null, status = RoomStatus.JOINED))
            onMicReleased(true)
        }

        Unit.toObservable()
    }.subscribeOnMainThread()

    override fun changePassword(oldPassword: String, newPassword: String): Completable {
        return Completable.defer {
            socketSubject.value.sendEventIgnoringResult("c_change_pwd", oldPassword.toMD5(), newPassword.toMD5())
        }.subscribeOn(AndroidSchedulers.mainThread())
                .doOnCompleted {
                    preference.userSessionToken = (preference.userSessionToken as? ExplicitUserToken)?.let { it.copy(password = newPassword) }
                }
    }

    private fun checkMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            throw AssertionError("This method must be called in main thread")
        }
    }

    private fun playSound(@RawRes res: Int) {
        soundPlayExecutor.submit {
            soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
        }
    }

    companion object {
        const val EVENT_SERVER_USER_LOGON = "s_logon"
        const val EVENT_SERVER_USER_LOGIN_FAILED = "s_login_failed"
        const val EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED = "s_member_update"
        const val EVENT_SERVER_SPEAKER_CHANGED = "s_speaker_changed"
        const val EVENT_SERVER_ROOM_INFO_CHANGED = "s_room_summary"
        const val EVENT_SERVER_INVITE_TO_JOIN = "s_invite_to_join"
        const val EVENT_SERVER_USER_KICK_OUT = "s_kick_out"

        const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
        const val EVENT_CLIENT_JOIN_ROOM = "c_join_room"
        const val EVENT_CLIENT_LEAVE_ROOM = "c_leave_room"
        const val EVENT_CLIENT_CONTROL_MIC = "c_control_mic"
        const val EVENT_CLIENT_RELEASE_MIC = "c_release_mic"
    }

}

private data class ExplicitUserToken(val userId : String,
                                     val password : String) : UserToken

private class UserObject(private val obj : JSONObject) : User {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getString("name")
    override val avatar: String?
        get() = obj.optString("avatar")
    override val permissions: Set<Permission>
        get() = obj.optString("privileges").toPermissionSet()
    override val priority: Int
        get() = obj.optInt("priority", Constants.DEFAULT_USER_PRIORITY)
}

private class GroupObject(private val obj : JSONObject) : Group {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getString("name")
    override val description: String?
        get() = obj.optString("description")
    override val avatar: String?
        get() = obj.optString("avatar")
    override val memberIds: Iterable<String>
        get() = obj.optJSONArray("members").toStringIterable()
}

private class RoomObject(private val obj : JSONObject) : Room {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getString("name")
    override val description: String?
        get() = obj.optString("description")
    override val ownerId: String
        get() = obj.getString("owner")
    override val associatedGroupIds: Iterable<String>
        get() = obj.optJSONArray("associatedGroupIds").toStringIterable()
    override val extraMemberIds: Iterable<String>
        get() = obj.optJSONArray("members").toStringIterable() //TODO: Update field
}

private class JoinRoomResponse(private val obj : JSONObject) {

    val room : Room = RoomObject(obj.getJSONObject("roomInfo"))

    val activeMemberIds : Iterable<String>
        get() = obj.getJSONArray("activeMembers").toStringIterable()

    val activeSpeakerId : String?
        get() = obj.optString("speaker")

    val serverConfiguration : Map<String, Any?>
        get() {
            val server = obj.getJSONObject("server")
            return mapOf(
                    "host" to server.getString("host"),
                    "port" to server.getInt("port"),
                    "protocol" to server.getString("protocol")
            )
        }
}

private fun String?.toPermissionSet() : Set<Permission> {
    if (this == null) {
        return emptySet()
    }

    //TODO:
    return emptySet()
}


private fun Array<Any?>.ensureNoError() {
    val arg = getOrNull(0)

    if (arg is JSONObject && arg.has("error")) {
        throw ServerException(arg.getString("error"))
    }
}

private fun CreateRoomRequest.toJSON(): JSONArray {
    // 0代表通讯组 1代表联系人
    return when (this) {
        is CreateRoomFromUser -> JSONArray().put(JSONObject().put("srcType", 1).put("srcData", userId))
        is CreateRoomFromGroup -> JSONArray().put(JSONObject().put("srcType", 0).put("srcData", groupId))
        else -> throw IllegalArgumentException("Unknown request type: " + this)
    }
}


private fun <T> Emitter.receiveEventRaw(eventName : String, clazz: Class<T>?) : Observable<T> {
    return Observable.create { subscriber ->
        val listener = Emitter.Listener {
            try {
                subscriber.onNext(clazz?.cast(it[0]))
            } catch(e: Exception) {
                subscriber.onError(e)
            }
        }

        on(eventName, listener)
        subscriber.add(Subscriptions.create { off(eventName, listener) })
    }
}

private inline fun <reified T : Any> Emitter.receiveEvent(eventName: String) : Observable<T> {
    return receiveEventRaw(eventName, T::class.java)
}

private fun Emitter.receiveEventIgnoringArguments(eventName: String) : Observable<Unit> {
    return receiveEventRaw(eventName, null)
}

private fun <T> Socket.sendEventRaw(eventName: String, clazz : Class<T>?, args: Array<out Any?>) : Single<T> {
    return Single.create { subscriber ->
        val ack = Ack {
            try {
                subscriber.onSuccess(clazz?.cast(it[0]))
            } catch(e: Exception) {
                subscriber.onError(e)
            }
        }

        emit(eventName, args, ack)
    }
}

private inline fun <reified T : Any> Socket.sendEvent(eventName: String, vararg args : Any?) : Single<T> {
    return sendEventRaw(eventName, T::class.java, args)
}

private fun Socket.sendEventIgnoringResult(eventName: String, vararg args : Any?) : Completable {
    return Completable.fromSingle(sendEventRaw<Any>(eventName, null, args))
}

private data class RoomInvitationImpl(override val roomId: String,
                                      override val inviterId: String,
                                      override val inviteTime: Date = Date()) : RoomInvitation