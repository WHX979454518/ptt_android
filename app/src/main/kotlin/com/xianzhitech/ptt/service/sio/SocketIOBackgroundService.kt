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
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.*
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.CreateRoomRequest
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.Subscriptions
import java.io.Serializable
import java.util.concurrent.TimeUnit
import kotlin.collections.*

class SocketIOBackgroundService : Service(), BackgroundServiceBinder {

    override var roomState = BehaviorSubject.create<RoomState>(RoomState())
    override var loginState = BehaviorSubject.create<LoginState>(LoginState())
    var serviceStateSubscription: Subscription? = null
    var loginSubscription : Subscription? = null
    var roomSubscription: CompositeSubscription? = null
    var currentTalkEngine: TalkEngine? = null

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    private lateinit var preferenceProvider : PreferenceStorageProvider
    private lateinit var userRepository : UserRepository
    private lateinit var groupRepository : GroupRepository
    private lateinit var contactRepository : ContactRepository
    private lateinit var roomRepository : RoomRepository
    private lateinit var talkEngineProvider: TalkEngineProvider
    private lateinit var btEngine: BtEngine
    private lateinit var soundPool: Pair<SoundPool, SparseIntArray>
    private lateinit var audioManager: AudioManager
    private lateinit var objectMapper: ObjectMapper
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
        objectMapper = appComponent.objectMapper

        vibrator = (getSystemService(VIBRATOR_SERVICE) as Vibrator).let { if (it.hasVibrator()) it else null }
        audioManager = (getSystemService(AUDIO_SERVICE) as AudioManager)

        soundPool = Pair(SoundPool(1, AudioManager.STREAM_MUSIC, 0), SparseIntArray()).apply {
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
                loginState.onNext(LoginState(currentUserID = lastUserId))
                doLogin(currentUserSessionToken.fromBase64ToSerializable() as Map<String, List<String>>).subscribe(GlobalSubscriber(this))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceStateSubscription?.unsubscribe()

        serviceStateSubscription = Observable.combineLatest(
                roomState.flatMap { state ->
                    roomRepository.optRoomWithMemberNames(state.currentRoomID).map { ExtraRoomState(state, it) }
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
                        builder.setSmallIcon(R.mipmap.ic_launcher)
                        builder.setContentTitle(R.string.app_name.toFormattedString(context))

                        when {
                            t.second.loginState.status == LoginState.Status.LOGGED_IN -> when (t.first.roomState.status) {
                                RoomState.Status.IDLE -> {
                                    builder.setContentText(R.string.notification_user_online.toFormattedString(context, t.second.logonUser?.name))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
                                }

                                RoomState.Status.JOINING -> {
                                    builder.setContentText(R.string.notification_joining_room.toFormattedString(context))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                }

                                else -> {
                                    builder.setContentText(R.string.notification_joined_room.toFormattedString(context, t.first.room?.getRoomName(context)))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                }
                            }

                            t.second.loginState.status == LoginState.Status.LOGIN_IN_PROGRESS -> {
                                if (t.third.not()) {
                                    builder.setContentText(R.string.notification_user_offline.toFormattedString(context, t.second.logonUser?.name))
                                } else if (t.first.roomState.status == RoomState.Status.IDLE) {
                                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(context))
                                } else {
                                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(context))
                                }
                            }

                            t.second.loginState.status == LoginState.Status.IDLE -> {
                                if (t.second.loginState.currentUserID == null) {
                                    stopForeground(true)
                                } else if (t.third.not() && t.second.logonUser != null) {
                                    builder.setContentText(R.string.notification_user_offline.toFormattedString(context, t.second?.logonUser?.name))
                                }
                                return
                            }
                        }

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
            reconnection = false
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
            add(getConnectivity()
                    .distinctUntilChanged()
                    .observeOnMainThread()
                    .subscribe {
                        if (!it && newSocket.connected()) {
                            newSocket.disconnect()
                        }

                        if (newSocket.connected().not()) {
                            newSocket.connect()
                            loginState.onNext(loginState.value.copy(status = LoginState.Status.LOGIN_IN_PROGRESS))
                        }
                    })

            add(newSocket.receiveEvent<User>(EVENT_SERVER_USER_LOGON)
                    .flatMap { user ->
                        if (preferenceProvider.lastLoginUserId != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preferenceProvider.lastLoginUserId = user.id
                            roomRepository.clearRooms().map { user }
                        } else {
                            user.toObservable()
                        }
                    }
                    .flatMap { user ->
                        // Do sync contacts
                        logd("Requesting syncing contacts")
                        newSocket.sendEvent<SyncContactsDTO>(EVENT_CLIENT_SYNC_CONTACTS, SyncContactsRequestDTO())
                                .flatMap { syncContactDto ->
                                    logd("Received sync result: $syncContactDto")
                                    userRepository.replaceAllUsers(syncContactDto.users.addUsers.toList() + user)
                                            .flatMap { groupRepository.replaceAllGroups(syncContactDto.groups.addGroups, syncContactDto.toGroupMemberMaps()) }
                                            .flatMap {
                                                contactRepository.replaceAllContacts(syncContactDto.users.addUsers.transform { it.id },
                                                        syncContactDto.groups.addGroups.transform { it.id })
                                            }
                                }
                                .map { user }
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

            add(newSocket.receiveEvent<RoomDTO>(EVENT_SERVER_INVITE_TO_JOIN)
                    .flatMap { dto -> roomRepository.updateRoom(dto, dto.memberIDs).map { dto } }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Room>() {
                        override fun onNext(t: Room) {
                            onInviteToJoin(t.id)
                        }
                    }))

        }

        socketSubject.onNext(newSocket)
    }.subscribeOnMainThread()

    internal fun onUserLogout(user: String) {
        logd("User $user has logged out")
        stopSelf()
    }

    internal fun onUserLogon(user : User) {
        logd("User $user has logged on")
        startService(Intent(this, javaClass))

        roomState.value.let {
            if (it.status != RoomState.Status.IDLE && it.currentRoomID != null) {
                logd("User has re-logon, trying to re-join the room")
                doJoinRoom(it.currentRoomID).subscribe(GlobalSubscriber(this))
            }
        }
    }

    internal fun onInviteToJoin(roomId: String) {
        logd("Received invite to join room $roomId")
        startActivity(Intent(this, RoomActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                .putExtra(RoomActivity.EXTRA_JOIN_ROOM_ID, roomId)
                .putExtra(RoomActivity.EXTRA_JOIN_ROOM_FROM_INVITE, true))
    }

    internal fun onRoomJoined(result: JoinRoomDTO) {
        if (btEngine.isBtMicEnable) {
            audioManager.mode = AudioManager.MODE_NORMAL
            btEngine.startSCO()
            roomSubscription?.add(btEngine.btMessage
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<String>() {
                        override fun onNext(t: String) {
                            when (t) {
                                BtEngine.MESSAGE_PUSH_DOWN -> requestMic().subscribe(GlobalSubscriber())
                                BtEngine.MESSAGE_PUSH_RELEASE -> releaseMic().subscribe(GlobalSubscriber())
                            }
                        }
                    }))
        } else {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        currentTalkEngine = talkEngineProvider.createEngine().apply {
            logd("Connecting to talk engine")
            connect(result.room.id, result.voiceServerInfo.toWebRTCEngineProperties(hashMapOf()).apply {
                this[WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID] = loginState.value.currentUserID
            })
        }
    }

    internal fun onRoomQuited(roomId: String) {
        if (btEngine.isBtMicEnable) {
            btEngine.stopSCO()
        }

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
     */
    override fun createRoom(request: CreateRoomRequest) = Observable.defer {
        logd("Creating room with $request")
        val socket = socketSubject.value
        val currentUserID = peekLoginState().currentUserID

        if (socket == null || currentUserID == null) {
            return@defer Observable.error<String>(IllegalStateException())
        }

        userRepository.getUser(currentUserID)
                .flatMap { user ->
                    if (user == null || user.privileges.contains(Privilege.CREATE_ROOM).not()) {
                        Observable.error<String>(StaticUserException(R.string.error_no_permission))
                    } else {
                        socket.sendRawEvent<RoomDTO>(EVENT_CLIENT_CREATE_ROOM, request.toJSON())
                                .flatMap { roomRepository.updateRoom(it, it.memberIDs) }
                                .map { it.id }
                    }
                }
    }.subscribeOnMainThread()


    internal fun doQuitCurrentRoom() {
        checkMainThread()
        val state = roomState.value
        val socket = socketSubject.value

        if (state.currentRoomID != null && socket != null) {
            roomSubscription = roomSubscription?.let { it.unsubscribe(); null }
            socket.sendEventIgnoreReturn(EVENT_CLIENT_LEAVE_ROOM, JoinRoomRequestDTO(state.currentRoomID)).subscribe(GlobalSubscriber())
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
            add(socketSubject.flatMap { socket ->
                // Subscribes to speaker change event
                add(socket.receiveEvent<SpeakerChangeDTO>(EVENT_SERVER_SPEAKER_CHANGED)
                        .observeOnMainThread()
                        .subscribeSafe {
                            val currentRoomState = peekRoomState()
                            if (it.roomID == roomId && currentRoomState.currentRoomActiveSpeakerID != it.speakerID) {
                                roomState.onNext(currentRoomState.copy(currentRoomActiveSpeakerID = it.speakerID))
                                it.speakerID?.let { onMicActivated(false) } ?: onMicReleased(false)
                            }
                        })

                // Subscribes to active member changes
                add(socket.receiveEvent<OnlineMemberChangeDTO>(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED)
                        .observeOnMainThread()
                        .subscribeSafe {
                            val currentRoomState = peekRoomState()
                            if (it.roomID == currentRoomState.currentRoomID) {
                                roomState.onNext(currentRoomState.copy(currentRoomOnlineMemberIDs = it.onlineMemberIDs))
                            }
                        })

                // Join the room
                socket.sendEvent<JoinRoomDTO>(EVENT_CLIENT_JOIN_ROOM, JoinRoomRequestDTO(roomId))
                        .flatMap { dto -> roomRepository.updateRoom(dto.room, dto.memberIDs).map { dto } }
            }.observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<JoinRoomDTO>() {
                        override fun onError(e: Throwable) = subscriber.onError(e)
                        override fun onNext(t: JoinRoomDTO) {
                            roomState.onNext(roomState.value.copy(
                                    status = RoomState.Status.JOINED,
                                    currentRoomOnlineMemberIDs = t.onlineMemberIDs,
                                    currentRoomActiveSpeakerID = t.speakerID))

                            onRoomJoined(t)
                            subscriber.onSingleValue(Unit)
                        }
                    }))
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
            return@create
        }

        roomState.onNext(currRoomState.copy(status = RoomState.Status.REQUESTING_MIC))
        currRoomSubscription.add(socket.sendEvent<MicResponseDTO>(EVENT_CLIENT_CONTROL_MIC, MicRequestDTO(currRoomState.currentRoomID))
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<MicResponseDTO>() {
                    override fun onError(e: Throwable) {
                        subscriber.onError(e)
                    }

                    override fun onNext(t: MicResponseDTO) {
                        if (t.success && t.speakerID == currUserId) {
                            roomState.onNext(currRoomState.copy(currentRoomActiveSpeakerID = currUserId))
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

        currRoomSubscription.add(socket.sendEventIgnoreReturn(EVENT_CLIENT_RELEASE_MIC, MicRequestDTO(currRoomState.currentRoomID))
                .observeOnMainThread()
                .subscribe(GlobalSubscriber()))

        if (currRoomState.currentRoomActiveSpeakerID == currUserId) {
            roomState.onNext(currRoomState.copy(currentRoomActiveSpeakerID = null))
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
        soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
    }


    internal inline fun <reified T : Any> Socket.receiveEvent(eventName: String) = Observable.create<T> { subscriber ->
        subscriber.onStart()
        val listener = { args: Array<Any> ->
            try {
                val value = args.getOrElse(0, { throw EmptyServerResponseException() })
                subscriber.onNext(objectMapper.convertValue<T>(value, T::class.java))
            } catch(e: Exception) {
                subscriber.onError(e)
            }
        }
        on(eventName, listener)
        subscriber.add(Subscriptions.create { off(eventName, listener) })
    }

    internal inline fun <reified T : Any> Socket.sendEvent(eventName: String, arg: Any? = null) = Observable.create<T> { subscriber ->
        val convertedArg: JSONObject?
        try {
            convertedArg = objectMapper.convertValue(arg, JSONObject::class.java)
        } catch(e: Exception) {
            subscriber.onError(e)
            return@create
        }

        logd("Sending event $eventName with arg $convertedArg")
        emit(eventName, arrayOf(convertedArg),
                Ack {
                    try {
                        it.ensureNoError()
                        val value = objectMapper.convertValue(it.getOrNull(0), T::class.java)
                        logd("Received $value for event $eventName with arg $arg")
                        subscriber.onSingleValue(value)
                    } catch(e: Exception) {
                        subscriber.onError(e)
                    }
                })
    }

    internal inline fun <reified T : Any> Socket.sendRawEvent(eventName: String, arg: Any? = null) = Observable.create<T> { subscriber ->
        logd("Sending event $eventName with arg $arg")
        emit(eventName, arrayOf(arg),
                Ack {
                    try {
                        it.ensureNoError()
                        val convertValue = objectMapper.convertValue(it.getOrNull(0), T::class.java)
                        logd("Received $convertValue for event $eventName with arg $arg")
                        subscriber.onSingleValue(convertValue)

                    } catch(e: Exception) {
                        subscriber.onError(e)
                    }
                })
    }

    internal fun Socket.sendEventIgnoreReturn(eventName: String, arg: Any? = null) = Observable.create<Unit> { subscriber ->
        logd("Sending event $eventName with arg $arg ignoring result")
        emit(eventName, arrayOf(objectMapper.convertValue(arg, JSONObject::class.java)),
                Ack {
                    try {
                        it.ensureNoError()
                        subscriber.onSingleValue(Unit)
                    } catch(e: Exception) {
                        subscriber.onError(e)
                    }
                })
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 100

        public const val EVENT_SERVER_USER_LOGON = "s_logon"
        public const val EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED = "s_member_update"
        public const val EVENT_SERVER_SPEAKER_CHANGED = "s_speaker_changed"
        public const val EVENT_SERVER_ROOM_INFO_CHANGED = "s_room_summary"
        public const val EVENT_SERVER_INVITE_TO_JOIN = "s_invite_to_join"

        public const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        public const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
        public const val EVENT_CLIENT_JOIN_ROOM = "c_join_room"
        public const val EVENT_CLIENT_LEAVE_ROOM = "c_leave_room"
        public const val EVENT_CLIENT_CONTROL_MIC = "c_control_mic"
        public const val EVENT_CLIENT_RELEASE_MIC = "c_release_mic"
    }

}