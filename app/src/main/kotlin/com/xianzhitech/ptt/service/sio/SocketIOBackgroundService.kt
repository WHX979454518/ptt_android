package com.xianzhitech.ptt.service.sio

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.provider.JoinRoomFromExisting
import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.ui.room.RoomActivity
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.io.Serializable
import kotlin.collections.*

class SocketIOBackgroundService : Service(), BackgroundServiceBinder {

    override var roomState = BehaviorSubject.create<RoomStateData>(RoomStateData())
    override var loginState = BehaviorSubject.create<LoginStateData>(LoginStateData())
    var loginSubscription : Subscription? = null
    var roomSubscription : Subscription? = null

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    private lateinit var preferenceProvider : PreferenceStorageProvider
    private lateinit var userRepository : UserRepository
    private lateinit var groupRepository : GroupRepository
    private lateinit var contactRepository : ContactRepository
    private lateinit var roomRepository : RoomRepository

    private var socket : Socket? = null

    override fun onCreate() {
        super.onCreate()

        val appComponent = application as AppComponent
        preferenceProvider = appComponent.preferenceProvider
        contactRepository = appComponent.contactRepository
        roomRepository = appComponent.roomRepository
        userRepository = appComponent.userRepository
        groupRepository = appComponent.groupRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun logout() {
        socket = socket?.let {
            stopSelf()
            it.close();
            null
        }

        loginSubscription = loginSubscription?.let { it.unsubscribe(); null }
    }

    override fun login(username: String, password: String): Observable<out LoginState> {
        return doLogin(mapOf(Pair("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))))
    }

    private fun doLogin(headers : Map<String, List<String>> = emptyMap()) = Observable.create<LoginState> { subscriber ->
        logout()

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
                        newSocket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, { it?.toSyncContactsDto() ?: throw ServerException("No contacts returned") })
                            .flatMap {
                                logd("Received sync result: $it")
                                userRepository.replaceAllUsers(it.users.toList() + (loginState.value.currentUser ?: throw IllegalStateException("User not logon")))
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
    }.subscribeOn(AndroidSchedulers.mainThread())


    internal fun onUserLogon(user : User) {
        logd("User $user has logged on")
    }

    internal fun onInviteToJoin(roomId: String) {
        logd("Received invite to join room $roomId")
        startActivity(RoomActivity.builder(this, JoinRoomFromExisting(roomId))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onBind(intent: Intent?) : IBinder = object : Binder(), BackgroundServiceBinder by this {}

    override fun requestJoinRoom(request: JoinRoomRequest): Observable<out RoomState> {
        val currRoomState = roomState.value
    }

    override fun requestQuitCurrentRoom() {
        throw UnsupportedOperationException()
    }

    override fun requestMic(): Observable<Boolean> {
        throw UnsupportedOperationException()
    }

    override fun releaseMic(): Observable<Unit> {
        throw UnsupportedOperationException()
    }

    data class RoomStateData(override val status : RoomState.Status = RoomState.Status.IDLE,
                             override val currentRoomId : String? = null,
                             override val activeMemberIds : Collection<String> = emptyList()) : RoomState

    data class LoginStateData(override val status : LoginState.Status = LoginState.Status.IDLE,
                              override val currentUser : User? = null) : LoginState


    companion object {
        private const val PREF_KEY_LOGIN_TOKEN = "key_login_token"
        private const val PREF_KEY_LAST_LOGIN_USER_ID = "key_last_login_user_id"

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