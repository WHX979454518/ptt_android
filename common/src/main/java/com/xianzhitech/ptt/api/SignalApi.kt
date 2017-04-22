package com.xianzhitech.ptt.api

import android.content.Context
import android.os.Looper
import com.fasterxml.jackson.core.type.TypeReference
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.primitives.Primitives
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.dto.Contact
import com.xianzhitech.ptt.api.dto.JoinWalkieRoomResponse
import com.xianzhitech.ptt.api.dto.MessageQuery
import com.xianzhitech.ptt.api.dto.MessageQueryResult
import com.xianzhitech.ptt.api.event.*
import com.xianzhitech.ptt.data.*
import com.xianzhitech.ptt.service.ServerException
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
import retrofit2.http.*
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
    val events: PublishSubject<Pair<String, Event>> = PublishSubject.create()

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

        appComponent.appApi.retrieveAppConfig(currentUser.value.orNull()?.id ?: "", appComponent.currentVersion.toString())
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

                    SIGNAL_MAPS.forEach { (signal, data) ->
                        socket.listen(signal) { arg ->
                            if (data is Class<*> && Event::class.java.isAssignableFrom(data) && arg != null) {
                                @Suppress("UNCHECKED_CAST")
                                events.onNext(signal to appComponent.objectMapper.convertValue(arg, data as Class<Event>))
                            } else if (data is Event) {
                                events.onNext(signal to data)
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

                        events.onNext("s_logon" to user!!)

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
        appComponent.preference.lastMessageSyncDate = null

        appComponent.storage.clear().logErrorAndForget().subscribe()

        connectionState.onNext(ConnectionState.IDLE)

    }

    fun syncContacts(version: Long): Maybe<Contact> {
        return Maybe.defer {
            Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
            restfulApi!!.syncContact(currentUser.value.get().id, currentUserCredentials!!.password.toMD5(), version)
                    .toMaybe()
                    .onErrorComplete { it is HttpException && it.code() == 304 }
        }
    }

    fun sendLocationData(locations: List<Location>): Completable {
        return Completable.defer {
            Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
            restfulApi!!.updateLocations(currentUser.value.get().id,
                    currentUserCredentials!!.password.toMD5(),
                    locations
            )
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

    private fun <T> Socket.listenOnce(name: String, clazz: Class<T>, callback: (arg: T?) -> Unit) {
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

    private fun String.guessLoginPostfix(): String {
        return when {
            matches(PHONE_MATCHER) -> ":PHONE"
            matches(EMAIL_MATCHER) -> ":MAIL"
            else -> ""
        }
    }

    fun createRoom(userIds: List<String>, groupIds: List<String>): Single<Room> {
        return Single.defer {
            Preconditions.checkArgument(userIds.isNotEmpty() || groupIds.isNotEmpty())

            rpc<Room>("c_create_room", null, groupIds, userIds).toSingle()
        }
    }

    fun joinWalkieRoom(roomId: String, fromInvitation: Boolean): Single<JoinWalkieRoomResponse> {
        return rpc<JoinWalkieRoomResponse>("c_join_room", roomId, fromInvitation).toSingle()
    }

    fun leaveWalkieRoom(roomId: String, askOthersToLeave: Boolean): Completable {
        return rpc<Unit>("c_leave_room", roomId, askOthersToLeave).ignoreElement()
    }

    fun grabWalkieMic(roomId: String): Single<Boolean> {
        return rpc<Boolean>("c_control_mic", roomId).toSingle()
    }

    fun releaseWalkieMic(roomId: String): Completable {
        return rpc<Unit>("c_release_mic", roomId).ignoreElement()
    }

    fun sendMessage(msg: Message): Single<Message> {
        return rpc<Message>("c_send_message", msg).toSingle()
    }

    private inline fun <reified T> rpc(name: String, vararg args: Any?): Maybe<T> {
        return Maybe.create { emitter ->
            Preconditions.checkState(socket != null && hasUser())

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

                if (T::class.java == Unit::class.java) {
                    emitter.onComplete()
                    return@emit
                }

                try {
                    if (response != null &&
                            response.optBoolean("success", false)) {
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
                } catch (err: Throwable) {
                    emitter.onError(err)
                }
            }
        }
    }

    fun getRoom(roomId: String): Single<Room> {
        return rpc<Room>("c_room_info", roomId).toSingle()
    }

    fun addRoomMembers(roomId: String, newMemberIds: List<String>): Single<Room> {
        return rpc<Room>("c_add_room_members", roomId, newMemberIds).toSingle()
    }

    fun inviteRoomMembers(roomId: String): Single<Int> {
        return rpc<Int>("c_invite_room_members", roomId).toSingle()
    }

    fun changePassword(oldPassword: String, password: String): Completable {
        return Completable.defer {
            val oldUserCredentials = currentUserCredentials!!
            rpc<Unit>("c_change_pwd", oldPassword.toMD5(), password.toMD5()).ignoreElement()
                    .doOnComplete {
                        val newCredentials = oldUserCredentials.copy(password = password)
                        currentUserCredentials = newCredentials
                        appComponent.preference.currentUserCredentials = newCredentials
                    }
        }
    }

    fun queryMessages(queries : List<MessageQuery>) : Single<List<MessageQueryResult>> {
        return rpc<Array<MessageQueryResult>>("c_query_messages", queries).toSingle().map { it.toList() }
    }

    private interface RestfulApi {

        @GET("/api/contact/sync/{idNumber}/{password}/{version}")
        fun syncContact(@Path("idNumber") idNumber: String,
                        @Path("password") password: String,
                        @Path("version") version: Long): Single<Contact>

        @POST("api/location")
        fun updateLocations(@Query("idNumber") idNumber: String,
                            @Query("password") password: String,
                            @Body locations: List<Location>): Completable

        @GET("api/nearby")
        fun findNearbyPeople(@Query("minLat") minLat: Double, @Query("minLng") minLng: Double, @Query("maxLat") maxLat: Double, @Query("maxLng") maxLng: Double): Single<JSONArray>
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
                "s_logon" to CurrentUser::class.java,
                "s_invite_to_join" to WalkieRoomInvitationEvent::class.java,
                "s_online_member_update" to WalkieRoomActiveInfoUpdateEvent::class.java,
                "s_speaker_changed" to WalkieRoomSpeakerUpdateEvent::class.java,
                "s_member_update" to Room::class.java,
                "s_user_update" to CurrentUser::class.java,
                "s_room_message" to Message::class.java,
                "s_kick_out_room" to RoomKickOutEvent
        )

    }
}