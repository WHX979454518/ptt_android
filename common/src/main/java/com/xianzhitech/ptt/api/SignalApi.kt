package com.xianzhitech.ptt.api

import android.content.Context
import android.os.Looper
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.primitives.Primitives
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.dto.*
import com.xianzhitech.ptt.api.event.Event
import com.xianzhitech.ptt.api.event.IceCandidateEvent
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.event.RequestLocationUpdateEvent
import com.xianzhitech.ptt.api.event.RoomKickOutEvent
import com.xianzhitech.ptt.api.event.UserKickedOutEvent
import com.xianzhitech.ptt.api.event.WalkieRoomActiveInfoUpdateEvent
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.api.event.WalkieRoomSpeakerUpdateEvent
import com.xianzhitech.ptt.data.ContactDepartment
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.LatLng
import com.xianzhitech.ptt.data.Location
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.UserCredentials
import com.xianzhitech.ptt.ext.hasActiveConnection
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toBase64
import com.xianzhitech.ptt.ext.toMD5
import com.xianzhitech.ptt.ext.toOptional
import com.xianzhitech.ptt.ext.w
import com.xianzhitech.ptt.ext.waitForConnection
import com.xianzhitech.ptt.service.ServerException
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
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.webrtc.IceCandidate
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class SignalApi(private val appComponent: AppComponent,
                private val appContext: Context) {

    private var retrieveAppConfigDisposable: Disposable? = null
    private var retryDisposable: Disposable? = null
    private var socket: Socket? = null
    private var restfulApi: RestfulApi? = null
    private var currentUserCredentials: UserCredentials? = null

    private val onlineUserIdSet = object : Set<String> {
        private val map = ConcurrentHashMap<String, Unit>()

        override val size: Int
            get() = map.size

        override fun contains(element: String): Boolean {
            return map.containsKey(element)
        }

        override fun containsAll(elements: Collection<String>): Boolean {
            throw UnsupportedOperationException()
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun iterator(): Iterator<String> {
            return map.keys().iterator()
        }

        fun add(element: String): Boolean {
            return map.put(element, Unit) == null
        }

        fun remove(element: String): Boolean {
            return map.remove(element) != null
        }

        fun clear() {
            map.clear()
        }
    }

    val currentUser: BehaviorSubject<Optional<CurrentUser>> = BehaviorSubject.createDefault(Optional.absent())
    val connectionState: BehaviorSubject<ConnectionState> = BehaviorSubject.createDefault(ConnectionState.IDLE)
    val events: PublishSubject<Pair<String, Event>> = PublishSubject.create()
    val onlineUserIds: BehaviorSubject<Set<String>> = BehaviorSubject.createDefault(onlineUserIdSet)

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

        appComponent.appApi.retrieveAppConfig(currentUser.value.orNull()?.id ?: "", appComponent.currentVersion)
                .observeOn(AndroidSchedulers.mainThread())
                .toMaybe()
                .logErrorAndForget(this::onConnectionEnd)
                .doOnSubscribe { retrieveAppConfigDisposable = it }
                .subscribe { config ->
                    logger.i { "Got app config $config" }

                    val options = IO.Options().apply {
                        multiplex = false
                        reconnection = false
                        transports = arrayOf("websocket")
                    }

                    val token = "$name:${password.toMD5()}${name.guessLoginPostfix()}"
                    val authHeader = "Basic ${token.toBase64()}"
                    val client = appComponent.httpClient.newBuilder()
                            .addNetworkInterceptor { chain ->
                                chain.request()
                                        .newBuilder()
                                        .addHeader("Authorization", authHeader)
                                        .addHeader("X-Device-Id", appComponent.preference.deviceId)
                                        .build()
                                        .let(chain::proceed)
                            }
                            .build()

                    val socket = IO.socket(config.signalServerEndpoint, options)
                    this@SignalApi.socket = socket
                    this@SignalApi.restfulApi = Retrofit.Builder()
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                            .addConverterFactory(JacksonConverterFactory.create(appComponent.objectMapper))
                            .client(client)
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
                        onConnectionEnd(it as? Throwable)
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

                    socket.listen("s_user_online") {
                        if (it != null && onlineUserIdSet.add(it.toString())) {
                            onlineUserIds.onNext(onlineUserIdSet)
                        }
                    }

                    socket.listen("s_user_offline") {
                        if (it != null && onlineUserIdSet.remove(it.toString())) {
                            onlineUserIds.onNext(onlineUserIdSet)
                        }
                    }

                    socket.io().on(Manager.EVENT_TRANSPORT, {
                        val transport = it.first() as Transport
                        transport.on(Transport.EVENT_REQUEST_HEADERS, {
                            @Suppress("UNCHECKED_CAST")
                            val headers = it.first() as MutableMap<String, List<String>>
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

        onlineUserIdSet.clear()

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

    fun uploadImage(imgBody: RequestBody): Single<String> {
        return restfulApi!!.uploadImage(MultipartBody.Part.createFormData("imgData", "image.jpg", imgBody))
    }

    fun syncContacts(version: Long): Maybe<Contact> {
        return waitForLoggedIn()
                .andThen(Maybe.defer {
                    Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
                    restfulApi!!.syncContact(version)
                            .toMaybe()
                            .onErrorComplete { it is HttpException && it.code() == 304 }
                })
    }

    fun sendLocationData(locations: List<Location>): Completable {
        if (locations.isEmpty()) {
            return Completable.complete()
        }

        return waitForLoggedIn()
                .andThen(Completable.defer {
                    Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
                    restfulApi!!.updateLocations(locations)
                })
    }

    fun getLastLocationByUserIds(userIds: List<String>):Maybe<List<LastLocationByUser>>{
        return waitForLoggedIn()
                .andThen(Maybe.defer {
                    Preconditions.checkState(restfulApi != null && hasUser() && currentUserCredentials != null)
                    restfulApi!!.getLastLocationByUserIds(userIds.joinToString(","))
                            .toMaybe()
                            .onErrorComplete { it is HttpException && it.code() == 304 }
                })
                .map {
                    if(it.data == null) {
                        return@map emptyList<LastLocationByUser>()
                    }

                    it.data
                }
    }

    private fun onConnectionEnd(err: Throwable? = null) {
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

        connectionState.onNext(ConnectionState.DISCONNECTED)

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

    fun joinVideoChat(roomId: String): Completable {
        return rpc<Unit>("c_join_video_chat", roomId).ignoreElement()
    }

    fun quitVideoChat(roomId: String): Completable {
        return rpc<Unit>("c_quit_video_chat", roomId).ignoreElement()
    }

    fun offerVideoChat(offer: String): Single<String> {
        return rpc<String>("c_offer", offer).toSingle()
    }

    fun sendIceCandidate(iceCandidate: IceCandidate): Completable {
        return rpc<Unit>("c_ice_candidate", JSONObject().apply {
            put("sdpMid", iceCandidate.sdpMid)
            put("candidate", iceCandidate.sdp)
            put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
        }).ignoreElement()
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

    fun getAllDepartments(): Single<List<ContactDepartment>> {
        return waitForLoggedIn()
                .doOnComplete {
                    logger.i { "User is logged in" }
                }
                .andThen(Single.defer { restfulApi!!.getAllDepartments() })
                .map {
                    if (it.data == null) {
                        return@map emptyList<ContactDepartment>()
                    }

                    // 舍弃无用的服务器数据
                    if (it.data.isNotEmpty() && it.data.last().parentObjectId == "-1") {
                        it.data.subList(0, it.data.size - 1)
                    } else {
                        it.data
                    }
                }
    }

    fun queryMessages(queries: List<MessageQuery>): Single<List<MessageQueryResult>> {
        return rpc<Array<MessageQueryResult>>("c_query_messages", queries).toSingle().map { it.toList() }
    }

    fun findNearByPeople(topLeft: LatLng, bottomRight: LatLng): Single<List<UserLocation>> {
        return waitForLoggedIn()
                .andThen(Single.defer { restfulApi!!.findNearbyPeople(topLeft.lat, topLeft.lng, bottomRight.lat, bottomRight.lng) })
    }

    fun findUserLocations(userIds: List<String>, startTime: Long, endTime: Long): Single<List<UserLocation>> {
        return waitForLoggedIn()
                .andThen(Single.defer { restfulApi!!.findUserLocations(userIds, startTime, endTime) })
    }

    private fun waitForLoggedIn(): Completable {
        return connectionState
                .filter { it == ConnectionState.IDLE || it == ConnectionState.CONNECTED }
                .firstElement()
                .flatMapCompletable {
                    if (it == ConnectionState.IDLE) {
                        Completable.error(UserNotLoggedInException)
                    } else {
                        Completable.complete()
                    }
                }
    }

    private data class Dto<T>(@JsonProperty("status") val status: Int?,
                              @JsonProperty("data") val data: T? = null,
                              @JsonProperty("message") val message: String? = null) {
        init {
            if (status != 200) {
                throw ServerException("unknown", message)
            }
        }
    }

    private interface RestfulApi {

        @GET("api/contact/sync/{version}")
        fun syncContact(@Path("version") version: Long): Single<Contact>

        @POST("api/location")
        fun updateLocations(@Body locations: List<Location>): Completable

        @GET("api/nearby")
        fun findNearbyPeople(@Query("minLat") minLat: Double, @Query("minLng") minLng: Double,
                             @Query("maxLat") maxLat: Double, @Query("maxLng") maxLng: Double): Single<List<UserLocation>>

        @GET("api/locations")
        fun findUserLocations(@Query("userIds[]") userIds: List<String>,
                              @Query("startTime") startTime: Long,
                              @Query("endTime") endTime: Long): Single<List<UserLocation>>

        @POST("upload/do/binary")
        @Multipart
        fun uploadImage(@Part file: MultipartBody.Part): Single<String>

        @GET("api/contact/departments")
        fun getAllDepartments(): Single<Dto<List<ContactDepartment>>>


        @GET("api/queryLastLoc")
        fun getLastLocationByUserIds(@Query("userId") userIds: String): Single<Dto<List<LastLocationByUser>>>

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
                "s_user_updated" to CurrentUser::class.java,
                "s_room_message" to Message::class.java,
                "s_ice_candidate" to IceCandidateEvent::class.java,
                "s_kick_out_room" to RoomKickOutEvent
        )

    }
}

