package com.xianzhitech.ptt.service.sio

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.support.annotation.RawRes
import android.support.v4.app.NotificationCompat
import android.util.SparseIntArray
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.NewBtEngine
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.repo.*
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.CreateRoomRequest
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SocketIOBackgroundService : Service(), BackgroundServiceBinder {

    override var roomState = BehaviorSubject.create<RoomState>(RoomState())
    override var loginState = BehaviorSubject.create<LoginState>(LoginState())
    var serviceStateSubscription: Subscription? = null
    var loginSubscription : Subscription? = null
    var roomSubscription: CompositeSubscription? = null
    var currentTalkEngine: TalkEngine? = null
    var btSubscription : Subscription? = null

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    private lateinit var preferenceProvider : PreferenceStorageProvider
    private lateinit var userRepository : UserRepository
    private lateinit var groupRepository : GroupRepository
    private lateinit var contactRepository : ContactRepository
    private lateinit var roomRepository : RoomRepository
    private lateinit var talkEngineProvider: TalkEngineProvider
    private lateinit var btEngine: NewBtEngine
    private lateinit var soundPool: Pair<SoundPool, SparseIntArray>
    private lateinit var audioManager: AudioManager
    private val soundPlayExecutor = Executors.newSingleThreadExecutor()
    private var vibrator : Vibrator? = null

    private var socketSubject = BehaviorSubject.create<Socket>()

    override fun onCreate() {
        super.onCreate()

        val appComponent = application as AppComponent
        preferenceProvider = appComponent.preferenceProvider
        contactRepository = appComponent.contactRepository
        roomRepository = appComponent.roomRepository
        userRepository = appComponent.userRepository
        groupRepository = appComponent.groupRepository
        talkEngineProvider = appComponent.talkEngineProvider
        btEngine = appComponent.btEngine

        vibrator = (getSystemService(VIBRATOR_SERVICE) as Vibrator).let { if (it.hasVibrator()) it else null }
        audioManager = (getSystemService(AUDIO_SERVICE) as AudioManager)

        soundPool = Pair(SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0), SparseIntArray()).apply {
            val context = this@SocketIOBackgroundService
            second.put(R.raw.incoming, first.load(context, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(context, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(context, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(context, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(context, R.raw.pttup_offline, 0))
        }

        val loginStateValue = loginState.value
        if (loginStateValue.currentUserID == null && loginStateValue.status == LoginState.Status.IDLE) {
            val currentUserSessionToken = preferenceProvider.userSessionToken
            val lastUserId = preferenceProvider.lastLoginUserId
            if (currentUserSessionToken != null && lastUserId != null) {
                loginState.onNext(LoginState(currentUserID = lastUserId, status = LoginState.Status.LOGIN_IN_PROGRESS))
                doLogin(currentUserSessionToken.fromBase64ToSerializable() as Map<String, List<String>>).subscribe(GlobalSubscriber(this))
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
                .subscribe(GlobalSubscriber())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceStateSubscription?.unsubscribe()

        serviceStateSubscription = Observable.combineLatest(
                roomState.flatMap { state ->
                    roomRepository.optRoomWithMembers(state.currentRoomID).map { ExtraRoomState(state, it) }
                },
                loginState.flatMap { state ->
                    userRepository.optUser(state.currentUserID).map { ExtraLoginState(state, it) }
                },
                getConnectivity(),
                { first, second, third -> Triple(first, second, third) })
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<Triple<ExtraRoomState, ExtraLoginState, Boolean>>() {
                    override fun onNext(t: Triple<ExtraRoomState, ExtraLoginState, Boolean>) {
                        val context = this@SocketIOBackgroundService
                        val builder = NotificationCompat.Builder(context)
                        builder.setOngoing(true)
                        builder.setAutoCancel(false)
                        builder.setContentTitle(R.string.app_name.toFormattedString(context))
                        val icon : Int

                        when (t.second.loginState.status) {
                            LoginState.Status.LOGGED_IN -> {
                                when (t.first.roomState.status) {
                                    RoomState.Status.IDLE -> {
                                        builder.setContentText(R.string.notification_user_online.toFormattedString(context, t.second.logonUser?.name))
                                        builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
                                        icon = R.drawable.ic_notification_logged_on
                                    }

                                    RoomState.Status.JOINING -> {
                                        builder.setContentText(R.string.notification_joining_room.toFormattedString(context))
                                        builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                        icon = R.drawable.ic_notification_joined_room
                                    }

                                    else -> {
                                        builder.setContentText(R.string.notification_joined_room.toFormattedString(context, t.first.room?.getRoomName(context)))
                                        builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                        icon = R.drawable.ic_notification_joined_room
                                    }
                                }
                            }

                            LoginState.Status.LOGIN_IN_PROGRESS -> {
                                if (t.third.not()) {
                                    builder.setContentText(R.string.notification_user_offline.toFormattedString(context, t.second.logonUser?.name))
                                } else if (t.first.roomState.status == RoomState.Status.IDLE) {
                                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(context))
                                } else {
                                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(context))
                                }

                                icon = R.drawable.ic_notification_logged_on
                            }

                            LoginState.Status.OFFLINE -> {
                                builder.setContentText(R.string.notification_user_offline.toFormattedString(context, t.second.logonUser?.name))
                                icon = R.drawable.ic_notification_offline
                            }

                            LoginState.Status.IDLE -> {
                                if (t.second.loginState.currentUserID == null) {
                                    stopForeground(true)
                                } else if (t.third.not() && t.second.logonUser != null) {
                                    builder.setContentText(R.string.notification_user_offline.toFormattedString(context, t.second.logonUser?.name))
                                }
                                return
                            }
                        }

                        builder.setSmallIcon(icon)
                        startForeground(SERVICE_NOTIFICATION_ID, builder.build())
                    }
                })

        return START_FLAG_REDELIVERY
    }

    override fun onDestroy() {
        serviceStateSubscription = serviceStateSubscription?.let {
            it.unsubscribe()
            null
        }
        super.onDestroy()
    }

    override fun logout() = Observable.defer { doLogout().toObservable() }.subscribeOnMainThread()

    internal fun doLogout() {
        loginState.value.currentUserID?.let {
            preferenceProvider.userSessionToken = null
            onUserLogout(it)
        }

        loginState.onNext(LoginState())

        socketSubject.value?.close()
        socketSubject = BehaviorSubject.create()
        stopSelf()

        loginSubscription = loginSubscription?.let { it.unsubscribe(); null }
    }

    override fun login(username: String, password: String) = Observable.defer {
        loginState.value.currentUserID?.let {
            doLogout()
        }

        doLogin(mapOf(Pair("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))))
    }.subscribeOnMainThread()

    internal fun doLogin(headers: Map<String, List<String>>) = Observable.create<Unit> { subscriber ->
        val newSocket = IO.socket((application as AppComponent).signalServerEndpoint, IO.Options().apply {
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
                        loginState.onNext(loginState.value.copy(status = LoginState.Status.OFFLINE))
                    })

            add(newSocket.receiveEvent(EVENT_SERVER_USER_LOGON)
                    .flatMap { response ->
                        logd("Login response: $response")

                        /**
                         * Response: { userObject }
                         */
                        val user = MutableUser().readFrom(response)

                        if (preferenceProvider.lastLoginUserId != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preferenceProvider.lastLoginUserId = user.id
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
                                    val users : MutableList<Any> = response.getJSONObject("enterpriseMembers").getJSONArray("add").transform { MutableUser().readFrom(it as JSONObject) }.toMutableList()
                                    val addGroupJsonArray = response.getJSONObject("enterpriseGroups").getJSONArray("add")
                                    val groups : MutableList<Any> = addGroupJsonArray.transform { MutableGroup().readFrom(it as JSONObject) }.toMutableList()
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
                            loginState.onNext(loginState.value.copy(status = LoginState.Status.IDLE))
                        }

                        override fun onNext(t: User) {
                            val newLoginState = LoginState(LoginState.Status.LOGGED_IN, t.id)
                            preferenceProvider.userSessionToken = (headers as Serializable).serializeToBase64()
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
                        val room = MutableRoom().readFrom(roomInfoJsonObj)
                        roomRepository.updateRoom(room, roomInfoJsonObj.getJSONArray("members").toStringIterable()).map { room }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Room>() {
                        override fun onNext(t: Room) {
                            onInviteToJoin(t.id)
                        }
                    }))

            // This must be in the end!
            add(getActiveNetwork()
                    .distinctUntilChanged()
                    .observeOnMainThread()
                    .subscribe {
                        logd("New active network: $it")
                        newSocket.disconnect()
                        if (it != null && it.isConnected) {
                            loginState.onNext(loginState.value.copy(status = LoginState.Status.LOGIN_IN_PROGRESS))
                            socketSubject.onNext(newSocket.connect())
                        }
                        else {
                            // Dispose talk engine while connection is lost
                            currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
                            roomState.value?.let {
                                if (it.status != RoomState.Status.IDLE) {
                                    roomState.onNext(it.copy(status = RoomState.Status.OFFLINE))
                                }
                            }

                            loginState.value?.let {
                                if (it.status != LoginState.Status.IDLE) {
                                    loginState.onNext(it.copy(status = LoginState.Status.OFFLINE))
                                }
                            }
                        }
                    })

        }

    }.subscribeOnMainThread()

    internal fun onUserLogout(user: String) {
        logd("User $user has logged out")
        stopSelf()
    }

    internal fun onUserLogon(user : User) {
        logd("User $user has logged on")
        startService(Intent(this, javaClass))
    }

    internal fun onInviteToJoin(roomId: String) {
        logd("Received invite to join room $roomId")
        startActivity(Intent(this, RoomActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                .putExtra(RoomActivity.EXTRA_JOIN_ROOM_ID, roomId)
                .putExtra(RoomActivity.EXTRA_JOIN_ROOM_FROM_INVITE, true))
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
                            NewBtEngine.MESSAGE_DEV_PTT_OK -> {
                                audioManager.mode = android.media.AudioManager.MODE_NORMAL
                                createTalkEngine(roomId, talkEngineProperties, true)
                            }
                            NewBtEngine.MESSAGE_DEV_PTT_DISCONNECTED -> {
                                createTalkEngine(roomId, talkEngineProperties, true)
                            }
                            NewBtEngine.MESSAGE_PUSH_DOWN -> requestMic().subscribe(GlobalSubscriber())
                            NewBtEngine.MESSAGE_PUSH_RELEASE -> releaseMic().subscribe(GlobalSubscriber())
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
                if (roomState.status == RoomState.Status.ACTIVE && roomState.currentRoomActiveSpeakerID == userState.currentUserID) {
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
            Observable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).subscribe {
                currentTalkEngine?.startSend()
            }
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

    override fun onBind(intent: Intent?) : IBinder = object : Binder(), BackgroundServiceBinder by this {}

    override fun requestJoinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
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
                                    val room = MutableRoom().readFrom(response)
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
        roomState.onNext(roomState.value.copy(status = RoomState.Status.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))

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
                                        status = newSpeakerID?.let { RoomState.Status.ACTIVE } ?: RoomState.Status.JOINED))
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
                        roomState.onNext(roomState.value.copy(status = RoomState.Status.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))

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
                                    roomRepository.updateRoom(MutableRoom().readFrom(roomInfoJsonObj), roomInfoJsonObj.getJSONArray("members").toStringIterable())
                                            .map { response }
                                }
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JSONObject>() {
                        override fun onError(e: Throwable) = subscriber.onError(e)
                        override fun onNext(t: JSONObject) {
                            val activeSpeakerID = if (t.isNull("speaker")) null else t.optString("speaker")
                            roomState.onNext(roomState.value.copy(
                                    status = activeSpeakerID?.let { RoomState.Status.ACTIVE } ?: RoomState.Status.JOINED,
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

    override fun requestQuitCurrentRoom() = Observable.defer { doQuitCurrentRoom().toObservable() }.subscribeOnMainThread()

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

        roomState.onNext(roomState.value.copy(status = RoomState.Status.REQUESTING_MIC))
        val micRequest = JSONObject().put("roomId", currRoomState.currentRoomID)
        currRoomSubscription.add(socket.sendEvent(EVENT_CLIENT_CONTROL_MIC, micRequest)
                .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<JSONObject>() {
                    override fun onError(e: Throwable) {
                        subscriber.onError(e)
                        // When error happens, always requests release mic to prevent further damage to the state
                        socket.sendEventIgnoreReturn(EVENT_CLIENT_RELEASE_MIC, micRequest).subscribe(GlobalSubscriber())
                        roomState.onNext(roomState.value.copy(status = RoomState.Status.JOINED))
                        playSound(R.raw.pttup_offline)
                    }

                    override fun onNext(t: JSONObject) {
                        /**
                         * Mic request: { roomId : "roomId" }
                         * Response: { success: true, speaker: "speaker ID" }
                         */

                        val newSpeakerID = if (t.isNull("speaker").not()) t.optString("speaker") else null
                        if (t.getBoolean("success") && newSpeakerID == currUserId) {
                            roomState.onNext(roomState.value.copy(currentRoomActiveSpeakerID = currUserId, status = RoomState.Status.ACTIVE))
                            onMicActivated(true)
                        }

                        subscriber.onSingleValue(Unit)
                    }
                }))

    }.subscribeOnMainThread()


    override fun releaseMic() = Observable.defer<Unit> {
        val socket = socketSubject.value
        val currRoomState = peekRoomState()
        val currRoomSubscription = roomSubscription
        val currUserId = loginState.value.currentUserID

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

        if (currRoomState.currentRoomActiveSpeakerID == currUserId) {
            roomState.onNext(currRoomState.copy(currentRoomActiveSpeakerID = null, status = RoomState.Status.JOINED))
            onMicReleased(true)
        }

        Unit.toObservable()
    }.observeOnMainThread()

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
        const val SERVICE_NOTIFICATION_ID = 100

        const val EVENT_SERVER_USER_LOGON = "s_logon"
        const val EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED = "s_member_update"
        const val EVENT_SERVER_SPEAKER_CHANGED = "s_speaker_changed"
        const val EVENT_SERVER_ROOM_INFO_CHANGED = "s_room_summary"
        const val EVENT_SERVER_INVITE_TO_JOIN = "s_invite_to_join"

        const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
        const val EVENT_CLIENT_JOIN_ROOM = "c_join_room"
        const val EVENT_CLIENT_LEAVE_ROOM = "c_leave_room"
        const val EVENT_CLIENT_CONTROL_MIC = "c_control_mic"
        const val EVENT_CLIENT_RELEASE_MIC = "c_release_mic"
    }

}