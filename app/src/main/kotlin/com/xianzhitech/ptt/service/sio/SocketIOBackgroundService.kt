package com.xianzhitech.ptt.service.sio

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.os.Looper
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
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.*
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
import kotlin.collections.*

class SocketIOBackgroundService : Service(), BackgroundServiceBinder {

    override var roomState = BehaviorSubject.create<RoomStateData>(RoomStateData())
    override var loginState = BehaviorSubject.create<LoginStateData>(LoginStateData())
    var serviceStateSubscription: Subscription? = null
    var loginSubscription : Subscription? = null
    var joinRoomSubscription: Subscription? = null
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

        soundPool = Pair(SoundPool(1, AudioManager.STREAM_MUSIC, 0), SparseIntArray()).apply {
            val context = this@SocketIOBackgroundService
            second.put(R.raw.incoming, first.load(context, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(context, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(context, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(context, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(context, R.raw.pttup_offline, 0))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceStateSubscription?.unsubscribe()

        serviceStateSubscription = Observable.combineLatest(roomState, loginState, { first, second -> Pair(first, second) })
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<Pair<RoomStateData, LoginStateData>>() {
                    override fun onNext(t: Pair<RoomStateData, LoginStateData>) {
                        val roomState = t.first
                        val loginState = t.second
                        val context = this@SocketIOBackgroundService
                        val builder = NotificationCompat.Builder(context)
                        builder.setOngoing(true)
                        builder.setAutoCancel(false)
                        builder.setTicker("ticker")
                        builder.setSmallIcon(R.mipmap.ic_launcher)
                        builder.setContentTitle(R.string.app_name.toFormattedString(context))

                        when (loginState.status) {
                            LoginState.Status.LOGGED_IN -> when (roomState.status) {
                                RoomState.Status.IDLE -> {
                                    builder.setContentText(R.string.notification_user_online.toFormattedString(context, loginState.currentUser?.name))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
                                }

                                RoomState.Status.JOINING -> {
                                    builder.setContentText(R.string.notification_joining_room.toFormattedString(context, roomState.currentRoom?.name))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                }

                                else -> {
                                    builder.setContentText(R.string.notification_joined_room.toFormattedString(context, roomState.currentRoom?.name))
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, RoomActivity::class.java), 0))
                                }
                            }

                            LoginState.Status.LOGIN_IN_PROGRESS -> {
                                if (roomState.status == RoomState.Status.IDLE) {
                                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(context))
                                } else {
                                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(context))
                                }
                            }

                            LoginState.Status.IDLE -> {
                                stopForeground(true)
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
        loginState.value.currentUser?.let {
            onUserLogout(it)
        }

        if (loginState.value.status != LoginState.Status.IDLE) {
            loginState.onNext(LoginStateData())
        }

        socket = socket?.let {
            stopSelf()
            it.close();
            null
        }

        loginSubscription = loginSubscription?.let { it.unsubscribe(); null }
    }

    override fun login(username: String, password: String): Observable<LoginState> {
        return doLogin(mapOf(Pair("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))))
    }

    internal fun doLogin(headers: Map<String, List<String>> = emptyMap()) = Observable.create<LoginState> { subscriber ->
        doLogout()

        val newSocket = IO.socket((application as AppComponent).signalServerEndpoint)

        loginState.onNext(LoginStateData(LoginState.Status.LOGIN_IN_PROGRESS))

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
            add(newSocket.onEvent(EVENT_SERVER_USER_LOGON, { it?.let { User().readFrom(it) } ?: throw ServerException("No user returned") })
                    .flatMap { user ->
                        if (preferenceProvider.get(PREF_KEY_LAST_LOGIN_USER_ID) != user.id) {
                            // Clear room information if it's this user's first login
                            logd("Clearing room database because different user has logged in")
                            preferenceProvider.save(PREF_KEY_LAST_LOGIN_USER_ID, user.id)
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
                                { it?.toSyncContactsDto() ?: throw ServerException("No contacts returned") },
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
                            loginState.onNext(LoginStateData(LoginState.Status.IDLE, null))
                        }

                        override fun onNext(t: User) {
                            val newLoginState = LoginStateData(LoginState.Status.LOGGED_IN, t)
                            preferenceProvider.save(PREF_KEY_LOGIN_TOKEN, headers as Serializable)
                            loginState.onNext(newLoginState)
                            subscriber.onNext(newLoginState)


                            onUserLogon(t)
                        }
                    }))

            add(newSocket.onEvent(EVENT_SERVER_INVITE_TO_JOIN, { it?.getString("roomId") ?: throw ServerException("No roomId specified") })
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<String>() {
                        override fun onNext(t: String) {
                            onInviteToJoin(t)
                        }
                    }))
        }

        socket = newSocket.connect()
    }.subscribeOnMainThread()

    internal fun onUserLogout(user: User) {
        logd("User $user has logged out")
        stopSelf()
    }

    internal fun onUserLogon(user : User) {
        logd("User $user has logged on")
        startService(Intent(this, javaClass))
    }

    internal fun onInviteToJoin(roomId: String) {
        logd("Received invite to join room $roomId")
        startActivity(RoomActivity.builder(this, JoinRoomFromExisting(roomId))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    internal fun onRoomQuited(room: Room) {
        if (btEngine.isBtMicEnable) {
            btEngine.stopSCO()
        }
    }

    internal fun onMicActivated(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.outgoing)
        } else {
            playSound(R.raw.incoming)
        }
    }

    internal fun onMicReleased(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.pttup)
        } else {
            playSound(R.raw.over)
        }
    }

    override fun onBind(intent: Intent?) : IBinder = object : Binder(), BackgroundServiceBinder by this {}

    override fun requestJoinRoom(request: JoinRoomRequest) = Observable.create<Unit> { subscriber ->
        roomState.value.let { state ->
            if (state.currentJoinRoomRequest == request) {
                logd("Request $request is being requested. Aborted")
                subscriber.onCompleted()
                return@create
            }

            // Cancel previous join room request if any
            joinRoomSubscription = joinRoomSubscription?.let { it.unsubscribe(); null }
            state.currentJoinRoomRequest?.let { roomState.onNext(state.copy(currentJoinRoomRequest = null)) }

            // Check if current room is important (thus we can't join another room)
            if (roomState.value.currentRoom?.important ?: false) {
                subscriber.onError(StaticUserException(R.string.error_room_is_important_to_quit))
                return@create
            }

            // If we don't have yet a room id, create one first
            val roomIdObservable: Observable<String>
            if (request is JoinRoomFromContact) {
                roomIdObservable = doCreateRoom(request).map { it.id }.observeOnMainThread()
            } else if (request is JoinRoomFromExisting) {
                roomIdObservable = request.roomId.toObservable()
            } else {
                subscriber.onError(StaticUserException(R.string.error_join_room))
                return@create
            }

            joinRoomSubscription = roomIdObservable
                    .subscribe(object : GlobalSubscriber<String>() {
                        override fun onError(e: Throwable) {
                            subscriber.onError(e)
                        }

                        override fun onNext(t: String) {
                            doJoinRoom(t).observeOnMainThread().subscribe(object : GlobalSubscriber<Unit>() {
                                override fun onError(e: Throwable) {
                                    subscriber.onError(e)
                                }

                                override fun onNext(t: Unit) {
                                    roomState.onNext(roomState.value.copy(currentJoinRoomRequest = null))
                                }
                            })
                        }

                    })
        }
    }.subscribeOnMainThread()


    internal fun doQuitCurrentRoom() {
        checkMainThread()
        roomState.value?.let { state ->
            state.currentRoom?.let { room ->
                roomSubscription = roomSubscription?.let { it.unsubscribe(); null }
                socket?.let {
                    it.sendEvent(EVENT_CLIENT_LEAVE_ROOM, { it }, JSONObject().put("roomId", room.id)).subscribe(GlobalSubscriber())
                    roomState.onNext(state.copy(status = RoomState.Status.IDLE, activeRoomID = null, currentRoom = null, activeRoomMemberIDs = emptySet()))
                    onRoomQuited(room)
                }
            }
        }

        currentTalkEngine = currentTalkEngine?.let { it.dispose(); null }
    }

    /**
     * Requests creating a room. If success, the room will be persisted to the local database.
     */
    internal fun doCreateRoom(request: JoinRoomFromContact): Observable<Room> {
        checkMainThread()
        logd("Creating room with $request")
        if (loginState.value.currentUser?.privileges?.contains(Privilege.CREATE_ROOM) ?: false) {
            return Observable.error(StaticUserException(R.string.error_no_permission))
        }

        return socket?.let {
            it.sendEvent(EVENT_CLIENT_CREATE_ROOM, { it ?: throw EmptyServerResponseException() })
                    .flatMap { roomRepository.updateRoom(Room().readFrom(it), it.getJSONArray("members").toStringIterable()) }
        } ?: Observable.error<Room>(IllegalStateException("Not connected to server"))
    }

    /**
     * Actually join room. This is assumed all pre-check has been done.
     * This method has to run on main thread.
     */
    internal fun doJoinRoom(roomId: String) = Observable.create<Unit> { subscriber ->
        doQuitCurrentRoom()
        roomState.onNext(roomState.value.copy(status = RoomState.Status.JOINING, activeRoomID = roomId, currentRoom = null, activeRoomMemberIDs = emptySet()))
        roomSubscription = CompositeSubscription().apply {
            logd("Joining room $roomId")
            socket?.let {

                // Listen for speaker changes
                add(it.onEvent(EVENT_SERVER_SPEAKER_CHANGED, { it ?: throw EmptyServerResponseException() })
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<JSONObject>() {
                            override fun onNext(t: JSONObject) {
                                val receivedRoomId = t.getString("roomId")
                                val receivedSpeakerId = if (t.isNull("speaker")) null else t.getString("speaker")
                                if (receivedRoomId == roomId && roomState.value.activeRoomSpeakerID != receivedSpeakerId) {
                                    roomState.onNext(roomState.value.copy(activeRoomSpeakerID = receivedSpeakerId))

                                    if (receivedSpeakerId != null) {
                                        onMicActivated(false)
                                    } else {
                                        onMicReleased(false)
                                    }
                                }
                            }
                        }))

                // Listen for active member changes
                add(it.onEvent(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED, { it ?: throw EmptyServerResponseException() })
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<JSONObject>() {
                            override fun onNext(t: JSONObject) {
                                val receivedRoomId = t.getString("roomId")
                                if (receivedRoomId == roomId) {
                                    roomState.onNext(roomState.value.copy(activeRoomMemberIDs = t.getJSONArray("activeMembers").toStringIterable().toSet()))
                                }
                            }
                        }))

                // Join the room!
                add(it.sendEvent(EVENT_CLIENT_JOIN_ROOM, { it ?: throw EmptyServerResponseException() }, JSONObject().put("roomId", roomId))
                        .flatMap { response ->
                            logd("Joining room response: $response")
                            val server = response.getJSONObject("server")
                            val (updatedRoom, memberIDs) = response.getJSONObject("roomInfo").let { Pair(Room().readFrom(it), it.getJSONArray("members").toStringIterable()) }

                            roomRepository.updateRoom(updatedRoom, memberIDs).map {
                                val engineProperties = mapOf(Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_IP, server.getString("host")),
                                        Pair(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID, loginState.value.currentUser?.id ?: throw UserNotLogonException()),
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
                                        currentRoom = t.room,
                                        activeRoomMemberIDs = t.activeMemberIDs,
                                        activeRoomSpeakerID = t.currentSpeaker))

                                onRoomJoined(t)
                            }
                        }))


            }
        }
    }.subscribeOnMainThread()

    override fun requestQuitCurrentRoom() = Observable.defer { doQuitCurrentRoom().toObservable() }.subscribeOnMainThread()

    override fun requestMic() = Observable.create<Unit> { subscriber ->
        socket?.let {
            roomState.value.let { state ->
                state.currentRoom?.let { room ->
                    roomSubscription?.add(it.sendEvent(EVENT_CLIENT_CONTROL_MIC,
                            { it?.getBoolean("success")?.and(it.getString("speaker") == loginState.value.currentUser?.id) ?: throw EmptyServerResponseException() },
                            JSONObject().put("roomId", room.id))
                            .onErrorReturn { false }
                            .observeOnMainThread()
                            .subscribe {
                                if (it) {
                                    roomState.onNext(state.copy(activeRoomSpeakerID = loginState.value.currentUser?.id))
                                    onMicActivated(true)
                                }
                                subscriber.onNext(null)
                                subscriber.onCompleted()
                            })
                } ?: subscriber.onError(IllegalStateException("Not in room"))
            }
        } ?: subscriber.onCompleted()
    }.observeOnMainThread()


    override fun releaseMic() = Observable.create<Unit> { subscriber ->
        socket?.let {
            roomState.value.let { state ->
                state.currentRoom?.let { room ->
                    roomSubscription?.add(it.sendEvent(EVENT_CLIENT_RELEASE_MIC, { it }, JSONObject().put("roomId", room.id))
                            .onErrorReturn { null }
                            .observeOnMainThread()
                            .subscribe())
                } ?: subscriber.onError(IllegalStateException("Not in room"))

                if (state.activeRoomSpeakerID == loginState.value.currentUser?.id) {
                    roomState.onNext(state.copy(activeRoomSpeakerID = null))
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

    data class RoomStateData(override val status : RoomState.Status = RoomState.Status.IDLE,
                             override val activeRoomID: String? = null,
                             val currentRoom: Room? = null,
                             override val activeRoomSpeakerID: String? = null,
                             override val currentJoinRoomRequest: JoinRoomRequest? = null,
                             override val activeRoomMemberIDs: Set<String> = emptySet()) : RoomState

    data class LoginStateData(override val status : LoginState.Status = LoginState.Status.IDLE,
                              override val currentUser : User? = null) : LoginState


    companion object {
        private const val PREF_KEY_LOGIN_TOKEN = "key_login_token"
        private const val PREF_KEY_LAST_LOGIN_USER_ID = "key_last_login_user_id"

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