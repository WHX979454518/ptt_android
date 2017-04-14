package com.xianzhitech.ptt.api

import android.content.Context
import android.os.Looper
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.dto.Contact
import com.xianzhitech.ptt.api.event.Event
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.event.RequestLocationUpdateEvent
import com.xianzhitech.ptt.api.event.UserKickedOutEvent
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.ext.*
import io.reactivex.Completable
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
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import java.util.concurrent.TimeUnit


class SignalApi(private val appComponent: AppComponent,
                private val appContext: Context) {

    private var retrieveAppConfigDisposable: Disposable? = null
    private var retryDisposable: Disposable? = null
    private var socket: Socket? = null
    private var restfulApi: RestfulApi? = null
    private var currentUserCredentials: Pair<String, String>? = null

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
        currentUserCredentials = name to password
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
                        currentUserCredentials = name to password
                        appComponent.preference.currentUser = user
                        appComponent.preference.currentUserCredentials = currentUserCredentials

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
        logger.i { "User request logging out" }

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

    fun syncContacts(version: Long): Single<Contact> {
        return Single.defer {
            Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
            restfulApi!!.syncContact(currentUser.value.get().id, currentUserCredentials!!.second.toMD5(), version)
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