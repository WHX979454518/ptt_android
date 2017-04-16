package com.xianzhitech.ptt.api

import android.content.Context
import android.os.Looper
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.primitives.Primitives
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.dto.Contact
import com.xianzhitech.ptt.api.event.Event
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.event.RequestLocationUpdateEvent
import com.xianzhitech.ptt.api.event.UserKickedOutEvent
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.UserCredentials
import com.xianzhitech.ptt.data.exception.ServerException
import com.xianzhitech.ptt.ext.*
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit


class SignalApi(private val appComponent: AppComponent,
                private val appContext: Context) {

    private var retrieveAppConfigDisposable: Disposable? = null
    private var retryDisposable: Disposable? = null
    private var socket: Socket? = null
    private var restfulApi: RestfulApi? = null
    private var currentUserCredentials: UserCredentials? = null

    val currentUser: BehaviorSubject<Optional<CurrentUser>> = BehaviorSubject.createDefault(Optional.absent())
    val connectionState: BehaviorSubject<ConnectionState> = BehaviorSubject.createDefault(ConnectionState.IDLE)
    val events: PublishSubject<Event> = PublishSubject.create()

    init {
        currentUser.onNext(appComponent.preference.currentUser.toOptional())
        currentUserCredentials = appComponent.preference.currentUserCredentials

        if (currentUserCredentials != null) {
            login()
        }
    }

    fun login(name: String, password: String) {
        currentUserCredentials = UserCredentials(name, password)
        login()
    }

    private fun login() {
        Preconditions.checkState(socket == null &&
                currentUserCredentials != null &&
                retrieveAppConfigDisposable == null &&
                Looper.myLooper() == Looper.getMainLooper())

        val (name, password) = currentUserCredentials!!

        logger.i { "Logging in with name $name" }

        if (hasUser()) {
            connectionState.onNext(ConnectionState.RECONNECTING)
        } else {
            connectionState.onNext(ConnectionState.CONNECTING)
        }

        appComponent.appApi.retrieveAppConfig(currentUser.value.orNull()?.id ?: "")
                .observeOn(AndroidSchedulers.mainThread())
                .toMaybe()
                .logErrorAndForget(this::onSocketError)
                .doOnSubscribe { retrieveAppConfigDisposable = it }
                .subscribe { config ->
                    logger.i { "Got app config $config" }

                    val options = IO.Options().apply {
                        multiplex = false
                        reconnection = false
                    }

                    val socket = IO.socket(config.signalServerEndpoint, options)
                    this@SignalApi.socket = socket
                    this@SignalApi.restfulApi = Retrofit.Builder()
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                            .addConverterFactory(JacksonConverterFactory.create(appComponent.objectMapper))
                            .baseUrl(config.signalServerEndpoint)
                            .build()
                            .create(RestfulApi::class.java)

                    SIGNAL_MAPS.forEach { signal, data ->
                        socket.listen(signal) { arg ->
                            if (data is Class<*> && Event::class.java.isAssignableFrom(data) && arg != null) {
                                @Suppress("UNCHECKED_CAST")
                                events.onNext(appComponent.objectMapper.convertValue(arg, data as Class<Event>))
                            } else if (data is Event) {
                                events.onNext(data)
                            } else {
                                throw IllegalArgumentException("Unknown data type $data or argument isn't correct")
                            }
                        }
                    }

                    socket.listen(Socket.EVENT_CONNECTING) {
                        if (hasUser()) {
                            connectionState.onNext(ConnectionState.RECONNECTING)
                        } else {
                            connectionState.onNext(ConnectionState.CONNECTING)
                        }
                    }

                    socket.listen(Socket.EVENT_ERROR) {
                        onSocketError(it as? Throwable)
                    }

                    socket.listenOnce("s_logon", CurrentUser::class.java) { user ->
                        currentUser.onNext(user.toOptional())
                        currentUserCredentials = UserCredentials(name, password)
                        appComponent.preference.currentUser = user
                        appComponent.preference.currentUserCredentials = currentUserCredentials

                        events.onNext(user)

                        connectionState.onNext(ConnectionState.CONNECTED)
                    }

                    socket.listenOnce("s_login_failed", Unit::class.java) {
                        logout()
                    }

                    socket.listenOnce("s_kick_out", Unit::class.java) {
                        logout()
                    }

                    socket.io().on(Manager.EVENT_TRANSPORT, {
                        val transport = it.first() as Transport
                        transport.on(Transport.EVENT_REQUEST_HEADERS, {
                            @Suppress("UNCHECKED_CAST")
                            val headers = it.first() as MutableMap<String, List<String>>
                            val token = "$name:${password.toMD5()}${name.guessLoginPostfix()}"
                            headers["Authorization"] = listOf("Basic ${token.toBase64()}")
                            headers["X-Device-Id"] = listOf(appComponent.preference.deviceId)
                        })
                    })

                    socket.connect()
                }
    }

    fun logout() {
        logger.i { "ContactUser request logging out" }

        retrieveAppConfigDisposable?.dispose()
        retrieveAppConfigDisposable = null

        socket?.off()
        socket?.close()
        socket = null

        retryDisposable?.dispose()
        retryDisposable = null

        currentUserCredentials = null
        currentUser.onNext(Optional.absent())
        appComponent.preference.currentUserCredentials = null
        appComponent.preference.currentUser = null

    }

    fun syncContacts(version: Long): Maybe<Contact> {
        return Maybe.defer {
            Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
            restfulApi!!.syncContact(currentUser.value.get().id, currentUserCredentials!!.password.toMD5(), version)
                    .toMaybe()
                    .onErrorComplete { it is HttpException && it.code() == 304 }
        }
    }

    private fun onSocketError(err: Throwable?) {
        logger.i { "Connection error: $err" }

        // Clear state
        retrieveAppConfigDisposable?.dispose()
        retrieveAppConfigDisposable = null

        socket?.off()
        socket?.close()
        socket = null

        restfulApi = null

        retryDisposable?.dispose()

        if (!hasUser()) {
            connectionState.onNext(ConnectionState.IDLE)
            currentUserCredentials = null
            return
        }

        if (appContext.hasActiveConnection()) {
            // Try again later
            retryDisposable = Completable.timer(10, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe(this::login)
        } else {
            // No network atm. Wait for network...
            retryDisposable = appContext.waitForConnection().observeOn(AndroidSchedulers.mainThread()).subscribe(this::login)
        }
    }

    private fun hasUser(): Boolean {
        return currentUser.value.isPresent
    }

    private fun <T> Socket.listenOnce(name: String, clazz : Class<T>, callback: (arg: T?) -> Unit) {
        once(name) { args ->
            logger.i { "Received raw event name = $name, args = ${args.toList()}" }

            AndroidSchedulers.mainThread().scheduleDirect {
                if (this != this@SignalApi.socket) {
                    logger.w { "Socket is no longer alive. Callback not allowed" }
                    return@scheduleDirect
                }

                if (clazz == Unit::class.java) {
                    callback(null)
                } else {
                    callback(args.firstOrNull()?.let { appComponent.objectMapper.convertValue(it, clazz) })
                }
            }
        }
    }

    private fun Socket.listen(name: String, callback: (arg: Any?) -> Unit) {
        on(name) { args ->
            logger.i { "Received raw event name = $name, args = ${args.toList()}" }

            AndroidSchedulers.mainThread().scheduleDirect {
                if (this != this@SignalApi.socket) {
                    logger.w { "Socket is no longer alive. Callback not allowed" }
                    return@scheduleDirect
                }

                callback(args.firstOrNull())
            }
        }
    }

    private fun String.guessLoginPostfix() : String {
        return when {
            matches(PHONE_MATCHER) -> ":PHONE"
            matches(EMAIL_MATCHER) -> ":MAIL"
            else -> ""
        }
    }

    fun createRoom(userIds: List<String>, groupIds: List<String>): Single<Room> {
        return Single.defer {
            Preconditions.checkArgument(userIds.isNotEmpty() || groupIds.isNotEmpty())
            Preconditions.checkState(socket != null && hasUser())

            rpc<Room>("c_create_room", null, groupIds, userIds).toSingle()
        }
    }

    fun sendMessage(msg : Message) : Single<Message> {
        return Single.defer {
            Preconditions.checkState(socket != null && hasUser())

            rpc<Message>("c_send_message", msg).toSingle()
        }
    }

    private inline fun <reified T> rpc(name : String, vararg args : Any?) : Maybe<T> {
        return Maybe.create { emitter ->
            Preconditions.checkState(socket != null)

            val convertedArgs = Array(args.size) { index ->
                val arg = args[index]
                when {
                    arg == null -> null
                    Primitives.isWrapperType(arg.javaClass) || arg.javaClass == String::class.java -> arg
                    Iterable::class.java.isAssignableFrom(arg.javaClass) || arg.javaClass.isArray -> appComponent.objectMapper.convertValue(arg, JSONArray::class.java)
                    else -> appComponent.objectMapper.convertValue(arg, JSONObject::class.java)
                }
            }

            logger.i { "Calling RPC(name=$name, args=${args.toList()}, convertedArgs=${convertedArgs.toList()})" }
            socket!!.emit(name, convertedArgs) { responses ->
                val response = responses.firstOrNull() as? JSONObject
                logger.i { "Calling RPC(name=$name), Response = $response" }
                try {
                    if (response != null && response.optBoolean("success", false)) {
                        if (response.has("data")) {
                            val data = response["data"]
                            when {
                                data == null -> emitter.onComplete()
                                T::class.java.isAssignableFrom(data::class.java) -> emitter.onSuccess(data as T)
                                else -> emitter.onSuccess(appComponent.objectMapper.convertValue(data, T::class.java))
                            }

                        } else {
                            emitter.onComplete()
                        }
                    } else if (response != null && response.get("error") is JSONObject) {
                        throw appComponent.objectMapper.convertValue(response.getJSONObject("error"), ServerException::class.java)
                    } else {
                        throw RuntimeException("Server's response for API call $name is invalid: $response")
                    }
                } catch (err : Throwable) {
                    emitter.onError(err)
                }
            }
        }
    }

    private interface RestfulApi {
        @GET("/api/contact/sync/{idNumber}/{password}/{version}")
        fun syncContact(@Path("idNumber") idNumber: String,
                        @Path("password") password: String,
                        @Path("version") version: Long): Single<Contact>

    }
    enum class ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        DISCONNECTED,

    }
    companion object {
        private val PHONE_MATCHER = Regex("^1[2-9]\\d{9}$")

        private val EMAIL_MATCHER = Regex(".+@.+\\..+$")
        private val logger = LoggerFactory.getLogger("Signal")
        private val SIGNAL_MAPS: Map<String, Any> = mapOf(
                "s_update_location" to RequestLocationUpdateEvent,
                "s_login_failed" to LoginFailedEvent::class.java,
                "s_kick_out" to UserKickedOutEvent,
                "s_logon" to CurrentUser::class.java
        )

    }
}