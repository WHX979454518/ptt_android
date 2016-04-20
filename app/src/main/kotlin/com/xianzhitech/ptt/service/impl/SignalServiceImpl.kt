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
import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.service.Service
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.observers.SafeSubscriber
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SignalServiceImpl(private val appContext: Context,
                        private val signalServerEndpoint : String,
                        private val preference: Preference,
                        private val userRepository : UserRepository,
                        private val groupRepository : GroupRepository,
                        private val contactRepository : ContactRepository,
                        private val roomRepository : RoomRepository,
                        private val talkEngineProvider: TalkEngineProvider,
                        private val btEngine: BtEngine) : SignalService {
    var loginSubscription : Subscription? = null
    var roomSubscription: CompositeSubscription? = null
    var requestMicSubscription : Subscription? = null
    var currentTalkEngine: TalkEngine? = null
    var btSubscription : Subscription? = null

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
            val currentUserSessionToken = preference.userSessionToken
            val lastUserId = preference.lastLoginUserId
            if (currentUserSessionToken != null && lastUserId != null) {
                loginState.onNext(LoginState(currentUserID = lastUserId, status = LoginStatus.LOGIN_IN_PROGRESS))
                doLogin(currentUserSessionToken.fromBase64ToSerializable() as Map<String, List<String>>).subscribeSimple()
            }
        }

        if (BuildConfig.DEBUG) {
            loginState.subscribe { logd("Login state: $it") }
            roomState.subscribe { logd("Room state: $it") }
        }

        // Record last active speaker to database
        roomState.distinctUntilChanged { it.currentRoomActiveSpeakerID }
                .flatMap {
                    if (it.currentRoomID != null && it.currentRoomActiveSpeakerID != null) {
                        roomRepository.updateRoomLastActiveUser(it.currentRoomID, it.currentRoomActiveSpeakerID)
                    }
                    else {
                        Observable.empty()
                    }
                }
                .subscribeSimple()
    }

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    override fun logout() = Observable.defer { doLogout().toObservable() }
            .subscribeOnMainThread()
            .doOnNext { appContext.stopService(Intent(appContext, Service::class.java)) }

    internal fun doLogout() {
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

        doLogin(mapOf(Pair("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))))
    }.subscribeOnMainThread()

    internal fun doLogin(headers: Map<String, List<String>>) = Observable.create<Unit> { subscriber ->
        val newSocket = IO.socket(signalServerEndpoint, IO.Options().apply {
            reconnection = true
            forceNew = true
        })

        // Process headers
        if (headers.isNotEmpty()) {
            newSocket.io().on(Manager.EVENT_TRANSPORT, {
                (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                    (it[0] as MutableMap<String, List<String>>).putAll(headers)
                })
            })
        }

        // Monitor user login
        loginSubscription = CompositeSubscription().apply {
            add(newSocket.io().receiveEvent(Manager.EVENT_RECONNECT)
                    .subscribe {
                        logd("Reconnecting to server...")
                        socketSubject.onNext(newSocket)
                    })

            add(newSocket.io().receiveEvent(Manager.EVENT_CONNECT_ERROR)
                    .observeOnMainThread()
                    .subscribe {
                        loginState.onNext(loginState.value.copy(status = LoginStatus.OFFLINE))
                    })

            add(newSocket.receiveEvent(EVENT_SERVER_USER_LOGON)
                    .flatMap { response ->
                        logd("Login response: $response")

                        /**
                         * Response: { userObject }
                         */
                        val user = createUser(response)

                        if (preference.lastLoginUserId != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preference.lastLoginUserId = user.id
                            roomRepository.clearRooms().map { user }
                        } else {
                            user.toObservable()
                        }
                    }
                    .flatMap { loggedInUser ->
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
                         *           add: [
                         *              {
                         *                inline groupObject,
                         *                members: [user IDs]
                         *              },
                         *              ...
                         *           ]
                         *       }
                         *   }
                         */
                        logd("Requesting syncing contacts")
                        newSocket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, JSONObject().put("enterMemberVersion", 1).put("enterGroupVersion", 1))
                                .flatMap { response ->
                                    logd("Received sync result: $response")
                                    val users : MutableList<Any> = response.getJSONObject("enterpriseMembers").getJSONArray("add").transform { createUser(it as JSONObject) }.toMutableList()
                                    val addGroupJsonArray = response.getJSONObject("enterpriseGroups").getJSONArray("add")
                                    val groups : MutableList<Any> = addGroupJsonArray.transform { createGroup(it as JSONObject) }.toMutableList()
                                    val groupMembers = addGroupJsonArray.toGroupsAndMembers()

                                    userRepository.replaceAllUsers(users as List<User>)
                                            .flatMap { userRepository.saveUser(loggedInUser) }
                                            .flatMap { groupRepository.replaceAllGroups(groups as List<Group>, groupMembers) }
                                            .flatMap {
                                                // Replaces users, groups with id.
                                                users.forEachIndexed { i, any -> users[i] = any.id }
                                                groups.forEachIndexed { i, any -> groups[i] = (any as Group).id }
                                                contactRepository.replaceAllContacts(users as List<String>, groups as List<String>)
                                            }
                                }
                                .map { loggedInUser }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<User>() {
                        override fun onError(e: Throwable) {
                            subscriber.onError(e)
                            loginState.onNext(loginState.value.copy(status = LoginStatus.IDLE))
                        }

                        override fun onNext(t: User) {
                            val newLoginState = LoginState(LoginStatus.LOGGED_IN, t.id)
                            preference.userSessionToken = (headers as Serializable).serializeToBase64()
                            loginState.onNext(newLoginState)
                            subscriber.onNext(null)
                            subscriber.onCompleted()

                            onUserLogon(t)
                        }
                    }))

            add(newSocket.receiveEvent(EVENT_SERVER_INVITE_TO_JOIN)
                    .flatMap { response ->
                        /**
                         * Response:
                         *  {
                         *    roomInfo : { roomObject, members: [user IDs] },
                         *    activeMembers: [active user IDs],
                         *    speaker: "speaker ID"
                         *  }
                         */
                        val roomInfoJsonObj = response.getJSONObject("roomInfo")
                        val room = createRoom(roomInfoJsonObj)
                        roomRepository.updateRoom(room, roomInfoJsonObj.getJSONArray("members").toStringIterable()).map { room }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Room>() {
                        override fun onNext(t: Room) {
                            onInviteToJoin(t)
                        }
                    }))

            add(newSocket.receiveEvent(EVENT_SERVER_USER_KICK_OUT)
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

    internal fun onUserKickedOut() {
        if (loginState.value.currentUserID != null) {
            logout().subscribe(GlobalSubscriber())
            appContext.startActivity(Intent(appContext, KickOutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    internal fun onUserLogout(user: String) {
        logd("User $user has logged out")
//        stopSelf()
    }

    internal fun onUserLogon(user : User) {
        logd("User $user has logged on")
//        startService(Intent(this, javaClass))
    }

    internal fun onInviteToJoin(room: Room) {
        logd("Received invite to join room $room")

        LocalBroadcastManager.getInstance(appContext)
                .sendBroadcast(Intent(SignalService.ACTION_INVITE_TO_JOIN)
                        .putExtra(SignalService.EXTRA_INVITE, InviteToJoinImpl(room.id, room.ownerId)))
    }


    internal fun onRoomJoined(roomId: String, talkEngineProperties : Map<String, Any?>) {
        //roomSubscription?.add()
        btSubscription =  btEngine.receiveCommand()
                .retry { i, throwable ->
                    logd("Got $throwable while listening for bluetooth event. Retrying time $i...")
                    throwable !is UnsupportedOperationException
                }
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<String>()
                {
                    override fun onNext(t: String) {
                        when (t) {
                            BtEngine.MESSAGE_DEV_PTT_OK -> {
                                audioManager.mode = android.media.AudioManager.MODE_NORMAL
                            }
                            BtEngine.MESSAGE_PUSH_DOWN -> requestMic().subscribe(GlobalSubscriber())
                            BtEngine.MESSAGE_PUSH_RELEASE -> releaseMic().subscribe(GlobalSubscriber())
                        }
                    }
                    override fun onError(e: Throwable)
                    {
                        //                        audioManager.mode = android.media.AudioManager.MODE_IN_CALL
                        //                        audioManager.requestAudioFocus(null, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        //设备不支持蓝牙,UnsupportedOperationException
                        if(e is UnsupportedOperationException)
                            return
                    }
                })
        //        roomSubscription?.add(btSubscription)
        //        audioManager.mode = AudioManager.MODE_IN_CALL
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

        createTalkEngine(roomId, talkEngineProperties, false)
    }

    internal fun createTalkEngine(roomId : String, talkEngineProperties: Map<String, Any?>, applyCurrentState : Boolean) {
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

    internal fun onRoomQuited(roomId: String) {
        btSubscription?.unsubscribe()
        btSubscription = null
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    internal fun onMicActivated(isSelf: Boolean) {
        if (isSelf) {
            vibrator?.vibrate(120)
            playSound(R.raw.outgoing)
            currentTalkEngine?.startSend()
        } else {
            playSound(R.raw.incoming)
        }
    }

    internal fun onMicReleased(isSelf: Boolean) {
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

        userRepository.getUser(currentUserID)
                .first()
                .flatMap { user ->
                    if (user == null || user.privileges.contains(Privilege.CREATE_ROOM).not()) {
                        Observable.error<String>(StaticUserException(R.string.error_no_permission))
                    } else {
                        socket.sendEvent(EVENT_CLIENT_CREATE_ROOM, request.toJSON())
                                .flatMap { response ->
                                    /**
                                     * Response:
                                     * {
                                     *      Room object,
                                     *      members : [user IDs]
                                     * }
                                     */
                                    val room = createRoom(response)
                                    roomRepository.updateRoom(room, response.getJSONArray("members").toStringIterable()).map { it.id }
                                }
                    }
                }
    }.subscribeOnMainThread()


    internal fun doQuitCurrentRoom() {
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
            socket.sendEventIgnoreReturn(EVENT_CLIENT_LEAVE_ROOM, JSONObject().put("roomId", state.currentRoomID)).subscribe(GlobalSubscriber())
            roomState.onNext(RoomState())
            onRoomQuited(state.currentRoomID)
        }

        currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
    }


    /**
     * Actually join room. This is assumed all pre-check has been done.
     * This method has to run on main thread.
     */
    internal fun doJoinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
        doQuitCurrentRoom()
        logd("Joining room $roomId")
        roomState.onNext(roomState.value.copy(status = RoomStatus.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))

        roomSubscription = CompositeSubscription().apply {
            val socketObservable = socketSubject.publish()

            // Subscribes to speaker change event
            add(socketObservable
                    .flatMap { it.receiveEvent(EVENT_SERVER_SPEAKER_CHANGED) }
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
                    .flatMap { socket -> socket.receiveEvent(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED) }
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
                        socket.sendEvent(EVENT_CLIENT_JOIN_ROOM, JSONObject().put("roomId", roomId))
                                .flatMap { response ->
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
                                    val roomInfoJsonObj = response.getJSONObject("roomInfo")
                                    roomRepository.updateRoom(createRoom(roomInfoJsonObj), roomInfoJsonObj.getJSONArray("members").toStringIterable())
                                            .map { response }
                                }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JSONObject>() {
                        override fun onError(e: Throwable) = subscriber.onError(e)
                        override fun onNext(t: JSONObject) {
                            val activeSpeakerID = if (t.isNull("speaker")) null else t.optString("speaker")
                            roomState.onNext(roomState.value.copy(
                                    status = activeSpeakerID?.let { RoomStatus.ACTIVE } ?: RoomStatus.JOINED,
                                    currentRoomActiveSpeakerID = activeSpeakerID,
                                    currentRoomOnlineMemberIDs = t.getJSONArray("activeMembers").toStringIterable().toSet()
                            ))

                            val voiceServerObj = t.getJSONObject("server")

                            onRoomJoined(roomId, hashMapOf(Pair(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID, loginState.value.currentUserID),
                                    Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_ADDRESS, voiceServerObj.getString("host")),
                                    Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT, voiceServerObj.getInt("port")),
                                    Pair(WebRtcTalkEngine.PROPERTY_PROTOCOL, voiceServerObj.getString("protocol"))))

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
                    socket.sendEvent(EVENT_CLIENT_CONTROL_MIC, micRequest)
                            .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
                .observeOnMainThread()
                .subscribe(SafeSubscriber(object : GlobalSubscriber<JSONObject>() {
                    override fun onError(e: Throwable) {
                        subscriber.onError(e)
                        // When error happens, always requests release mic to prevent further damage to the state
                        socket.sendEventIgnoreReturn(EVENT_CLIENT_RELEASE_MIC, micRequest).subscribe(GlobalSubscriber())
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
        currRoomSubscription.add(socket.sendEventIgnoreReturn(EVENT_CLIENT_RELEASE_MIC, JSONObject().put("roomId", currRoomState.currentRoomID))
                .observeOnMainThread()
                .subscribe(GlobalSubscriber()))

        if (currRoomState.currentRoomActiveSpeakerID == currUserId || currRoomState.currentRoomActiveSpeakerID == null) {
            roomState.onNext(currRoomState.copy(currentRoomActiveSpeakerID = null, status = RoomStatus.JOINED))
            onMicReleased(true)
        }

        Unit.toObservable()
    }.subscribeOnMainThread()

    internal fun checkMainThread() {
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