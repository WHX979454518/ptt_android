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
import com.xianzhitech.ptt.Constants
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
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
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
    private var vibrator : Vibrator? = null

    private var socket : Socket? = null

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
                    state.currentRoomID?.let {
                        roomRepository.getRoomWithMemberNames(it, Constants.MAX_MEMBER_DISPLAY_COUNT).map { ExtraRoomData(state, it) }
                    } ?: ExtraRoomData(state).toObservable()
                },
                loginState.flatMap { state ->
                    state.currentUserID?.let {
                        userRepository.getUser(it).map { ExtraLoginData(state, it) }
                    } ?: ExtraLoginData(state).toObservable()
                },
                getConnectivity(),
                { first, second, third -> Triple(first, second, third) })
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<Triple<ExtraRoomData, ExtraLoginData, Boolean>>() {
                    override fun onNext(t: Triple<ExtraRoomData, ExtraLoginData, Boolean>) {
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

        socket = socket?.let {
            stopSelf()
            it.close();
            null
        }

        loginSubscription = loginSubscription?.let { it.unsubscribe(); null }
    }

    override fun login(username: String, password: String) = Observable.defer {
        loginState.value.currentUserID?.let {
            doLogout()
        }

        doLogin(mapOf(Pair("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))))
    }.subscribeOnMainThread()

    internal fun doLogin(headers: Map<String, List<String>>) = Observable.create<Unit> { subscriber ->
        val newSocket = IO.socket((application as AppComponent).signalServerEndpoint)

        loginState.onNext(loginState.value.copy(status = LoginState.Status.LOGIN_IN_PROGRESS))

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
            add(newSocket.onEvent(EVENT_SERVER_USER_LOGON, { User().readFrom(it) })
                    .flatMap { user ->
                        if (preferenceProvider.lastLoginUserId != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preferenceProvider.lastLoginUserId = user.id
                            roomRepository.clearRooms().map { user }
                        }
                        else {
                            user.toObservable()
                        }
                    }
                    .flatMap { user ->
                        // Do sync contacts
                        logd("Requesting syncing contacts")
                        newSocket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS,
                                { it.toSyncContactsDto() },
                                JSONObject().put("enterMemberVersion", 1).put("enterGroupVersion", 1))
                                .flatMap {
                                    logd("Received sync result: $it")
                                    userRepository.replaceAllUsers(it.users.toList() + user)
                                            .concatWith(groupRepository.replaceAllGroups(it.groups, it.groupMemberMap))
                                            .concatWith(contactRepository.replaceAllContacts(it.users.transform { it.id }, it.groups.transform { it.id }))
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

            add(newSocket.onJsonObjectEvent(EVENT_SERVER_INVITE_TO_JOIN)
                    .flatMap { response ->
                        val (updatedRoom, memberIDs) = response.getJSONObject("roomInfo").let { Pair(Room().readFrom(it), it.getJSONArray("members").toStringIterable()) }
                        roomRepository.updateRoom(updatedRoom, memberIDs)
                    }
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Room>() {
                        override fun onNext(t: Room) {
                            onInviteToJoin(t.id)
                        }
                    }))
        }

        socket = newSocket.connect()
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

    internal fun onRoomJoined(result: JoinRoomResult) {
        currentTalkEngine = talkEngineProvider.createEngine().apply {
            logd("Connecting to talk engine")
            connect(result.room.id, result.engineProperties)
        }

        if (btEngine.isBtMicEnable) {
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
        }
    }

    internal fun onRoomQuited(roomId: String) {
        if (btEngine.isBtMicEnable) {
            btEngine.stopSCO()
        }
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
    override fun createRoom(request: CreateRoomRequest): Observable<String> {
        logd("Creating room with $request")

        val userObservable = loginState.value.currentUserID?.let { userRepository.getUser(it) } ?: Observable.just<User?>(null)

        return userObservable
                .observeOnMainThread()
                .flatMap { user ->
                    if (user?.privileges?.contains(Privilege.CREATE_ROOM)?.not() ?: false) {
                        Observable.error<String>(StaticUserException(R.string.error_no_permission))
                    } else {
                        socket?.let {
                            it.sendEvent(EVENT_CLIENT_CREATE_ROOM, { it }, request.toJSON())
                                    .flatMap { roomRepository.updateRoom(Room().readFrom(it), it.getJSONArray("members").toStringIterable()) }
                                    .map { it.id }
                        } ?: Observable.error<String>(IllegalStateException("Not connected to server"))
                    }
                }
    }

    internal fun doQuitCurrentRoom() {
        checkMainThread()
        roomState.value?.let { state ->
            state.currentRoomID?.let { roomId ->
                roomSubscription = roomSubscription?.let { it.unsubscribe(); null }
                socket?.let {
                    it.sendEvent(EVENT_CLIENT_LEAVE_ROOM, { it }, JSONObject().put("roomId", roomId)).subscribe(GlobalSubscriber())
                    onRoomQuited(roomId)
                }

            }
            roomState.onNext(state.copy(status = RoomState.Status.IDLE, currentRoomID = null, currentRoomOnlineMemberIDs = emptySet()))
        }

        currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
    }



    /**
     * Actually join room. This is assumed all pre-check has been done.
     * This method has to run on main thread.
     */
    internal fun doJoinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
        doQuitCurrentRoom()
        roomState.onNext(roomState.value.copy(status = RoomState.Status.JOINING, currentRoomID = roomId, currentRoomOnlineMemberIDs = emptySet()))
        roomSubscription = CompositeSubscription().apply {
            logd("Joining room $roomId")
            socket?.let {

                // Listen for speaker changes
                add(it.onJsonObjectEvent(EVENT_SERVER_SPEAKER_CHANGED)
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<JSONObject>() {
                            override fun onNext(t: JSONObject) {
                                val receivedRoomId = t.getString("roomId")
                                val receivedSpeakerId = if (t.isNull("speaker")) null else t.getString("speaker")
                                if (receivedRoomId == roomId && roomState.value.currentRoomActiveSpeakerID != receivedSpeakerId) {
                                    roomState.onNext(roomState.value.copy(currentRoomActiveSpeakerID = receivedSpeakerId))

                                    if (receivedSpeakerId != null) {
                                        onMicActivated(false)
                                    } else {
                                        onMicReleased(false)
                                    }
                                }
                            }
                        }))

                // Listen for active member changes
                add(it.onJsonObjectEvent(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED)
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<JSONObject>() {
                            override fun onNext(t: JSONObject) {
                                val receivedRoomId = t.getString("roomId")
                                if (receivedRoomId == roomId) {
                                    roomState.onNext(roomState.value.copy(currentRoomOnlineMemberIDs = t.getJSONArray("activeMembers").toStringIterable().toSet()))
                                }
                            }
                        }))

                // Join the room!
                add(it.sendEvent(EVENT_CLIENT_JOIN_ROOM, { it }, JSONObject().put("roomId", roomId))
                        .flatMap { response ->
                            logd("Joining room response: $response")
                            val server = response.getJSONObject("server")
                            val (updatedRoom, memberIDs) = response.getJSONObject("roomInfo").let { Pair(Room().readFrom(it), it.getJSONArray("members").toStringIterable()) }

                            roomRepository.updateRoom(updatedRoom, memberIDs).map {
                                val engineProperties = mapOf(Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_IP, server.getString("host")),
                                        Pair(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID, loginState.value.currentUserID ?: throw UserNotLogonException()),
                                        Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT, server.getInt("port")),
                                        Pair(WebRtcTalkEngine.PROPERTY_PROTOCOL, server.getString("protocol")))

                                val activeMemberIDs = response.getJSONArray("activeMembers").toStringIterable().toSet()
                                val currentSpeaker = response.optString("speaker")

                                JoinRoomResult(updatedRoom, activeMemberIDs, currentSpeaker, engineProperties)
                            }
                        }
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<JoinRoomResult>() {
                            override fun onError(e: Throwable) {
                                subscriber.onError(e)
                            }

                            override fun onNext(t: JoinRoomResult) {
                                roomState.onNext(roomState.value.copy(
                                        status = RoomState.Status.JOINED,
                                        currentRoomOnlineMemberIDs = t.activeMemberIDs,
                                        currentRoomActiveSpeakerID = t.currentSpeaker))

                                onRoomJoined(t)
                                subscriber.onNext(Unit)
                                subscriber.onCompleted()
                            }
                        }))


            }
        }
    }.subscribeOnMainThread()

    override fun requestQuitCurrentRoom() = Observable.defer { doQuitCurrentRoom().toObservable() }.subscribeOnMainThread()

    override fun requestMic() = Observable.create<Unit> { subscriber ->
        socket?.let {
            roomState.value.let { state ->
                state.currentRoomID?.let { roomId ->
                    roomState.onNext(state.copy(status = RoomState.Status.REQUESTING_MIC))

                    roomSubscription?.add(it.sendEvent(EVENT_CLIENT_CONTROL_MIC,
                            { it.getBoolean("success").and(it.getString("speaker") == loginState.value.currentUserID) },
                            JSONObject().put("roomId", roomId))
                            .onErrorReturn { false }
                            .observeOnMainThread()
                            .subscribe {
                                if (it) {
                                    roomState.onNext(state.copy(currentRoomActiveSpeakerID = loginState.value.currentUserID))
                                    onMicActivated(true)
                                }
                                subscriber.onNext(Unit)
                                subscriber.onCompleted()
                            })
                } ?: subscriber.onError(IllegalStateException("Not in room"))
            }
        } ?: subscriber.onCompleted()
    }.observeOnMainThread()


    override fun releaseMic() = Observable.create<Unit> { subscriber ->
        socket?.let {
            roomState.value.let { state ->
                state.currentRoomID?.let { roomId ->
                    roomSubscription?.add(it.sendEvent(EVENT_CLIENT_RELEASE_MIC, { it }, JSONObject().put("roomId", roomId))
                            .onErrorReturn { null }
                            .observeOnMainThread()
                            .subscribe())
                } ?: subscriber.onError(IllegalStateException("Not in room"))

                if (state.currentRoomActiveSpeakerID == loginState.value.currentUserID) {
                    roomState.onNext(state.copy(currentRoomActiveSpeakerID = null))
                    onMicReleased(true)
                }
            }
        }

        subscriber.onCompleted()
    }.observeOnMainThread()

    internal fun checkMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            throw AssertionError("This method must be called in main thread")
        }
    }

    private fun playSound(@RawRes res: Int) {
        soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
    }

    internal data class ExtraRoomData(val roomState: RoomState,
                                      val room: RoomWithMemberNames? = null)

    internal data class ExtraLoginData(val loginState: LoginState,
                                       val logonUser: User? = null)

    /**
     * 加入房间的返回结果
     */
    data class JoinRoomResult(val room: Room,
                              val activeMemberIDs: Set<String>,
                              val currentSpeaker: String?,
                              val engineProperties: Map<String, Any?>)


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