package com.xianzhitech.ptt.ui.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.toBase64
import com.xianzhitech.ptt.ext.toMD5
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.provider.*
import com.xianzhitech.ptt.ui.room.RoomActivity
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.Subscriptions
import java.io.Serializable
import kotlin.collections.*

interface RoomState {
    enum class Status {
        IDLE,
        JOINING,
        JOINED,
        REQUESTING_MIC,
        ACTIVE,
    }

    val status : Status
    val currentRoomId : String?
    val activeMemberIds : Collection<String>
}

interface LoginState {
    enum class Status {
        IDLE,
        LOGIN_IN_PROGRESS,
        LOGGED_IN,
    }

    val status : Status
    val currentUser : Person?
}

interface BackgroundServiceBinder {
    val roomState : Observable<out RoomState>
    val loginState : Observable<out LoginState>

    fun peekRoomState() : RoomState
    fun peekLoginState() : LoginState

    fun login(username : String, password : String) : Observable<out LoginState>
    fun logout()

    fun requestJoinRoom() : Observable<out RoomState>
    fun requestQuitCurrentRoom()
}

class BackgroundService : Service(), BackgroundServiceBinder {

    override var roomState = BehaviorSubject.create<RoomStateData>(RoomStateData())
    override var loginState = BehaviorSubject.create<LoginStateData>(LoginStateData())
    var loginSubscription : Subscription? = null
    var roomSubscription : Subscription? = null

    override fun peekRoomState() = roomState.value
    override fun peekLoginState() = loginState.value

    private lateinit var preferenceProvider : PreferenceStorageProvider
    private lateinit var contactRepository : ContactRepository

    private var socket : Socket? = null

    override fun onCreate() {
        super.onCreate()

        val appComponent = application as AppComponent
        preferenceProvider = appComponent.preferenceProvider
        contactRepository = appComponent.contactRepository
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
            newSocket.io().on(io.socket.client.Manager.EVENT_TRANSPORT, {
                (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                    (it[0] as MutableMap<String, List<String>>).putAll(headers)
                })
            })
        }

        // Monitor user login
        loginSubscription = CompositeSubscription().apply {
            add(newSocket.onEvent(EVENT_SERVER_USER_LOGON, { it?.let { Person().readFrom(it) } ?: throw ServerException("No user returned") })
                    .first()
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Person>() {
                        override fun onError(e: Throwable) {
                            subscriber.onError(e)
                            loginState.onNext(LoginStateData(LoginState.Status.IDLE, null))
                        }

                        override fun onNext(t: Person) {
                            val newLoginState = LoginStateData(LoginState.Status.LOGGED_IN, t)
                            preferenceProvider.save("saved_user", headers as Serializable)
                            loginState.onNext(newLoginState)
                            subscriber.onNext(newLoginState)
                        }
                    }))

            add(newSocket.onEvent(EVENT_SERVER_INVITE_TO_JOIN, { it?.getString("roomId") ?: throw ServerException("No roomId specified")})
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<String>() {
                        override fun onNext(t: String) {
                            onInviteToJoin(t)
                        }
                    }))
        }

        socket = newSocket.connect()
    }.subscribeOn(AndroidSchedulers.mainThread())

    private fun onInviteToJoin(roomId: String) {
        startActivity(RoomActivity.builder(this, ConversationFromExisting(roomId))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onBind(intent: Intent?) : IBinder = object : Binder(), BackgroundServiceBinder by this {}

    private inline fun <T> parseSeverResult(args : Array<Any?>, mapper : (JSONObject) -> T) : T {
        val arg = args.getOrElse(0, { throw ServerException("No response") })

        if (arg is JSONObject && arg.has("error")) {
            throw ServerException(arg.getString("error"))
        }

        return mapper(arg as JSONObject)
    }

    private inline fun <T> Socket.onEvent(event: String, crossinline mapper : (JSONObject?) -> T) = Observable.create<T> { subscriber ->
        val listener: (Array<Any?>) -> Unit = {
            subscriber.onStart()
            try {
                subscriber.onNext(parseSeverResult(it, mapper))
            } catch(e: Exception) {
                subscriber.onError(e)
            }
        }
        on(event, listener)

        subscriber.add(Subscriptions.create { off(event, listener) })
    }

    private inline fun <T> Socket.sendEvent(eventName: String, crossinline resultMapper: (JSONObject?) -> T, vararg args: Any?) = Observable.create<T> { subscriber ->
        subscriber.onStart()
        emit(eventName, *args, { args : Array<Any?> ->
            try {
                subscriber.onNext(parseSeverResult(args, resultMapper))
            } catch(e: Throwable) {
                subscriber.onError(e)
            } finally {
                subscriber.onCompleted()
            }
        })
    }

    override fun requestJoinRoom(): Observable<out RoomState> {
        throw UnsupportedOperationException()
    }

    override fun requestQuitCurrentRoom() {
        throw UnsupportedOperationException()
    }

    data class RoomStateData(override val status : RoomState.Status = RoomState.Status.IDLE,
                             override val currentRoomId : String? = null,
                             override val activeMemberIds : Collection<String> = emptyList()) : RoomState

    data class LoginStateData(override val status : LoginState.Status = LoginState.Status.IDLE,
                              override val currentUser : Person? = null) : LoginState


    companion object {
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