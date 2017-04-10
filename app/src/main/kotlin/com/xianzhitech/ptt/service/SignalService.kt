package com.xianzhitech.ptt.service

import android.content.Context
import android.os.PowerManager
import com.baidu.mapapi.model.LatLngBounds
import com.google.gson.Gson
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.dto.JoinRoomResult
import com.xianzhitech.ptt.service.dto.NearbyUser
import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
import com.xianzhitech.ptt.service.handler.ForceUpdateException
import com.xianzhitech.ptt.util.Range
import io.socket.client.Ack
import io.socket.client.IO.Options
import io.socket.client.IO.socket
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Duration
import org.threeten.bp.LocalTime
import org.threeten.bp.ZonedDateTime
import org.webrtc.IceCandidate
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import rx.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("SignalService")

class SignalService(val appContext : Context,
                    val authTokenFactory: () -> String,
                    val signalFactory: SignalFactory,
                    val deviceIdFactory : () -> Single<String>,
                    val appConfigFactory : () -> Single<AppConfig>,
                    val okHttpClient: OkHttpClient,
                    val gson: Gson) {

    private val signalSubject = PublishSubject<Signal>()

    val signals : Observable<Signal>
        get() = signalSubject

    private var wakeLock : PowerManager.WakeLock? = null
    private var socket : Socket? = null
    private var appConfig : AppConfig? = null
    private val restService : SignalRestService by lazy {
        Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(object : Converter.Factory() {
                    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>?, retrofit: Retrofit?): Converter<ResponseBody, *>? {
                        when (type) {
                            JSONObject::class.java -> return Converter<ResponseBody, JSONObject> {
                                val ret = JSONObject(it.string())
                                if (ret.has("status")) {
                                    if (ret.optInt("status", 200) != 200) {
                                        throw UnknownServerException
                                    }

                                    ret.optJSONObject("data")
                                }
                                else {
                                    ret
                                }
                            }
                            JSONArray::class.java -> return Converter<ResponseBody, JSONArray> {
                                val ret = JSONObject(it.string())
                                if (ret.optInt("status", 200) != 200) {
                                    throw UnknownServerException
                                }

                                ret.optJSONArray("data") }
                            else -> return null
                        }

                    }
                })
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHttpClient.newBuilder().readTimeout(0, TimeUnit.HOURS).addNetworkInterceptor {
                    it.proceed(it.request().newBuilder().addHeader("Authorization", authTokenFactory()).build())
                }.build())
                .baseUrl(appConfig!!.signalServerEndpoint)
                .build()
                .create(SignalRestService::class.java)
    }

    fun login() : Completable {
        signalSubject += ConnectionSignal(ConnectionEvent.CONNECTING)
        return appConfigFactory()
                .zipWith(deviceIdFactory(), { uri, id -> uri to id})
                .observeOnMainThread()
                .doOnError { signalSubject += ConnectionSignal(ConnectionEvent.ERROR, it) }
                .flatMapCompletable { pair ->
                    val (appConfig, deviceId) = pair
                    this@SignalService.appConfig = appConfig
                    if (appConfig.hasUpdate && appConfig.mandatory) {
                        signalSubject += ForceUpdateSignal(appConfig)
                        throw ForceUpdateException(appConfig)
                    }

                    val uri = appConfig.signalServerEndpoint
                    Completable.create(Completable.OnSubscribe { subscriber ->
                        socket?.let {
                            it.off()
                            it.disconnect()
                        }

                        if (wakeLock == null) {
                            wakeLock = (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "signal_service")
                        }

                        if (wakeLock!!.isHeld.not()) {
                            wakeLock!!.acquire()
                        }

                        val s = socket(uri, Options().apply {
                            multiplex = false
                            transports = arrayOf("websocket")
                        })

                        s.onMainThread(Socket.EVENT_CONNECT, { logger.i { "Connected to $uri" } })
                        s.onMainThread(Socket.EVENT_CONNECTING, {
                            logger.i { "Connecting to $uri" }
                            signalSubject += ConnectionSignal(ConnectionEvent.CONNECTING)
                        })

                        s.onMainThread(Socket.EVENT_RECONNECTING, {
                            logger.i { "Reconnecting to $uri" }
                            signalSubject += ConnectionSignal(ConnectionEvent.CONNECTING)
                        })

                        s.io().on(Manager.EVENT_PING, { logger.i { "Ping!" } })
                        s.io().on(Manager.EVENT_PONG, { logger.i { "Pong!" } })


                        s.io().on(Manager.EVENT_TRANSPORT, {
                            val transport = it.first() as Transport
                            transport.on(Transport.EVENT_REQUEST_HEADERS, {
                                @Suppress("UNCHECKED_CAST")
                                val headers = it.first() as MutableMap<String, List<String>>
                                headers["Authorization"] = listOf(authTokenFactory())
                                headers["X-Device-Id"] = listOf(deviceId)
                            })
                        })

                        s.onMainThread(Socket.EVENT_CONNECT_ERROR, {
                            logger.e(it.firstOrNull() as? Throwable) { "Error connecting to $uri: ${it.toList()}" }
                            signalSubject += ConnectionSignal(ConnectionEvent.ERROR, it.firstOrNull() as? Throwable)
                            subscriber.onError(it.firstOrNull() as? Throwable)
                        })

                        signalFactory.signalNames.forEach { name ->
                            s.onMainThread(name, {
                                val sig = signalFactory.createSignal(name, *it)
                                if (sig != null) {
                                    signalSubject += sig

                                    when (sig) {
                                        is UserLoggedInSignal -> subscriber.onCompleted()
                                        is UserLoginFailSignal -> {
                                            subscriber.onError(sig.err)
                                            logout()
                                        }
                                    }
                                }
                            })
                        }

                        s.connect()
                        socket = s
                    }).subscribeOn(AndroidSchedulers.mainThread())
                }
    }

    fun logout() {
        Completable.fromCallable {
            val s = socket
            socket = null

            s?.let {
                it.off()
                it.disconnect()
            }

            wakeLock?.release()
            wakeLock = null
            Unit
        }.subscribeOn(AndroidSchedulers.mainThread()).subscribeSimple()
    }

    fun <R, V> sendCommand(cmd: Command<R, V>) {
        when {
            cmd is UpdateLocationCommand && cmd.locations.size > Constants.MAX_LOCATIONS_TO_SEND_VIA_WS  -> {
                appConfig ?: return cmd.resultSubject.onError(StaticUserException(com.xianzhitech.ptt.R.string.error_user_not_logon))

                restService.updateLocations(cmd.locations)
                        .subscribe(object : CompletableSubscriber {
                            override fun onSubscribe(d: Subscription?) {
                            }

                            override fun onError(e: Throwable?) {
                                cmd.resultSubject.onError(e)
                            }

                            override fun onCompleted() {
                                cmd.resultSubject.onNext(Unit)
                            }
                        })
            }

            cmd is FindNearbyPeopleCommand -> {
                appConfig ?: return cmd.resultSubject.onError(StaticUserException(com.xianzhitech.ptt.R.string.error_user_not_logon))

                restService.findNearbyPeople(cmd.latLngBounds.southwest.latitude, cmd.latLngBounds.southwest.longitude,
                        cmd.latLngBounds.northeast.latitude, cmd.latLngBounds.northeast.longitude)
                        .subscribe(object : SingleSubscriber<JSONArray>() {
                            override fun onError(error: Throwable?) {
                                cmd.resultSubject.onError(error)
                            }

                            override fun onSuccess(value: JSONArray) {
                                cmd.resultSubject.onNext(value)
                            }
                        })
            }

            cmd is SyncContactCommand -> {
                appConfig ?: return cmd.resultSubject.onError(StaticUserException(com.xianzhitech.ptt.R.string.error_user_not_logon))

                restService.syncContact(cmd.userId, cmd.password, cmd.version)
                        .subscribe(object : SingleSubscriber<JSONObject>() {
                            override fun onError(error: Throwable?) {
                                cmd.resultSubject.onError(error)
                            }

                            override fun onSuccess(value: JSONObject) {
                                cmd.resultSubject.onNext(value)
                            }
                        })
            }

            else -> Completable.fromCallable {
                socket?.emit(cmd.cmd, cmd.args, Ack {
                    logger.d { "Received ${cmd.cmd} result: ${it.print()}" }
                    try {
                        val resultObj = it.first() as JSONObject
                        if (resultObj.optBoolean("success", false)) {
                            if (resultObj.has("data")) {
                                @Suppress("UNCHECKED_CAST")
                                cmd.resultSubject.onNext(resultObj.get("data") as V)
                            }
                            else {
                                cmd.resultSubject.onNext(null)
                            }
                        } else {
                            cmd.resultSubject.onError(resultObj.getJSONObject("error").toError())
                        }
                    } catch(e: Exception) {
                        cmd.resultSubject.onError(e)
                    }
                })
            }.subscribeOn(AndroidSchedulers.mainThread()).subscribeSimple()
        }
    }

    private fun Socket.onMainThread(name : String, action : (Array<Any>) -> Unit) {
        on(name, {
            logger.i { "Received event $name with args: ${it.toList()}" }
            Completable.fromCallable {
                if (this@SignalService.socket != this) {
                    logger.w { "Event received with stale socket" }
                }
                else {
                    action(it)
                }
            }.subscribeOn(AndroidSchedulers.mainThread()).subscribeSimple()
        })
    }

    private fun Socket.onceMainThread(name : String, action: (Array<Any>) -> Unit) {
        once(name, {
            logger.i { "Received event $name with args: ${it.toList()}" }
            Completable.fromCallable {
                if (this@SignalService.socket != this) {
                    logger.w { "Event received with stale socket" }
                }
                else {
                    action(it)
                }
            }.subscribeOn(AndroidSchedulers.mainThread()).subscribeSimple()
        })
    }

}

private interface SignalRestService {
    @POST("api/location")
    fun updateLocations(@Body locations : List<Location>) : Completable

    @GET("api/contact/sync/{userId}/{password}/{version}")
    fun syncContact(@Path("userId") userId : String, @Path("password") password : String, @Path("version") version : Long) : Single<JSONObject>

    @GET("api/nearby")
    fun findNearbyPeople(@Query("minLat") minLat : Double, @Query("minLng") minLng : Double, @Query("maxLat") maxLat : Double, @Query("maxLng") maxLng : Double) : Single<JSONArray>
}

interface Signal

interface SignalFactory {
    fun createSignal(name : String, vararg obj : Any?) : Signal?
    val signalNames : List<String>
}

enum class ConnectionEvent {
    CONNECTING,
    ERROR,
}

data class ConnectionSignal(val event: ConnectionEvent,
                            val err : Throwable? = null) : Signal

data class UserLoggedInSignal(val user : UserObject) : Signal
data class UserLoginFailSignal(val err : Throwable) : Signal
data class UserKickOutSignal(val reason : String? = null) : Signal
data class RoomInviteSignal(val invitation: RoomInvitation) : Signal
data class RoomUpdateSignal(val room : Room) : Signal
data class RoomOnlineMemberUpdateSignal(val update : RoomOnlineMemberUpdate) : Signal
data class RoomSpeakerUpdateSignal(val update : RoomSpeakerUpdate) : Signal
data class RoomKickOutSignal(val roomId: String) : Signal
data class UserUpdatedSignal(val user : UserObject) : Signal
data class ForceUpdateSignal(val appConfig: AppConfig) : Signal
data class IceCandidateSignal(val iceCandidate: IceCandidate) : Signal

object UpdateLocationSignal : Signal


class DefaultSignalFactory : SignalFactory {
    companion object {
        private val allSignals = listOf<Pair<String, (Array<out Any?>) -> Signal>>(
                "s_login_failed" to { obj -> UserLoginFailSignal((obj.first() as? JSONObject).toError()) },
                "s_logon" to { obj -> UserLoggedInSignal(UserObject(obj.first() as JSONObject)) },
                "s_kick_out" to { obj -> UserKickOutSignal() },
                "s_invite_to_join" to { obj -> RoomInviteSignal(RoomInvitationObject(obj.first() as JSONObject)) } ,
                "s_member_update" to { obj -> RoomUpdateSignal(RoomObject(obj.first() as JSONObject)) } ,
                "s_online_member_update" to { obj -> RoomOnlineMemberUpdateSignal(RoomActiveInfoUpdate(obj.first() as JSONObject)) } ,
                "s_speaker_changed" to { obj -> RoomSpeakerUpdateSignal(RoomSpeakerUpdateObject(obj.first() as JSONObject)) } ,
                "s_kick_out_room" to { obj -> RoomKickOutSignal(obj.first().toString()) },
                "s_user_updated" to { args -> UserUpdatedSignal(UserObject(args.first() as JSONObject)) },
                "s_user_locate" to { args -> UpdateLocationSignal },
                "s_ice_candidate" to { args ->
                    val obj = args.first() as JSONObject
                    IceCandidateSignal(iceCandidate = IceCandidate(obj.getString("sdpMid"),
                            obj.getInt("sdpMLineIndex"),
                            obj.getString("candidate")))
                }
        )
    }

    override fun createSignal(name: String, vararg obj: Any?): Signal? {
        try {
            return allSignals.firstOrNull { it.first == name }?.second?.invoke(obj)
        } catch(e: Exception) {
            logger.e(e) { "Error creating signal for $name" }
            return null
        }
    }

    override val signalNames: List<String>
        get() = allSignals.map { it.first }
}

open class Command<R, V>(val cmd : String,
                         vararg args: Any?)  {
    val resultSubject : BehaviorSubject<V> = BehaviorSubject.create()
    open val args : Array<out Any?> = args

    fun getAsync() : Single<R> {
        return resultSubject.map { convert(it) }.first().toSingle()
    }

    open fun convert(value : V) : R {
        return value as R
    }

    override fun toString(): String = "${javaClass.simpleName}(name='$cmd', args=${args.print()})"
}


interface RoomInvitation : Serializable {
    val room: Room
    val inviterId: String
    val inviteTime: Date
    val inviterPriority: Int
    val force: Boolean
}

data class CreateRoomRequest(val name: String? = null,
                             val groupIds: Collection<String> = emptyList(),
                             val extraMemberIds: Collection<String> = emptyList()) {
    init {
        if (groupIds.sizeAtLeast(1).not() && extraMemberIds.sizeAtLeast(1).not()) {
            throw IllegalArgumentException("GroupId and MemberIds can't be null in the same time")
        }
    }
}

class CreateRoomCommand(name : String? = null,
                        groupIds: Iterable<String> = emptyList(),
                        extraMemberIds: Iterable<String> = emptyList()) : Command<Room, JSONObject>("c_create_room", name, groupIds.toJSONArray(), extraMemberIds.toJSONArray()) {
    init {
        if (groupIds.sizeAtLeast(1).not() && extraMemberIds.sizeAtLeast(1).not()) {
            throw IllegalArgumentException("GroupId and MemberIds can't be null in the same time")
        }
    }

    override fun convert(value: JSONObject): Room {
        return RoomObject(value)
    }
}

class JoinGroupChatCommand(roomId: String) : Command<Any, Any>("c_join_video_chat", roomId)
class QuitGroupChatCommand(roomId: String) : Command<Any, Any>("c_quit_video_chat", roomId)
class OfferGroupChatCommand(offer : String) : Command<String, String>("c_offer", offer)

class AddIceCandidateCommand(iceCandidate: IceCandidate) : Command<Any, Any>("c_ice_candidate", JSONObject().apply {
    put("sdpMid", iceCandidate.sdpMid)
    put("candidate", iceCandidate.sdp)
    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
})

data class SyncContactResult(val version : Long,
                             val users : List<User>,
                             val groups : List<Group>) {
    override fun toString(): String {
        return "SyncContactResult{version=$version, userCount=${users.size}, groupCount=${groups.size}}"
    }
}

class FindNearbyPeopleCommand(val latLngBounds: LatLngBounds) : Command<List<NearbyUser>, JSONArray>("") {
    override fun convert(value: JSONArray): List<NearbyUser> {
        return value.transform { NearbyUser.fromJSON(it as JSONObject) }
    }
}

class SyncContactCommand(val userId : String,
                         val password: String,
                         val version : Long) : Command<SyncContactResult, JSONObject>("") {

    override fun convert(value: JSONObject): SyncContactResult {
        return SyncContactResult(version = value.getLong("version"),
                users = value.optJSONArray("enterpriseMembers")?.transform { UserObject(it as JSONObject) } ?: emptyList(),
                groups = value.optJSONArray("enterpriseGroups")?.transform { GroupObject(it as JSONObject) } ?: emptyList()
        )
    }
}

class UpdateLocationCommand(val locations : List<Location>,
                            val userId : String) : Command<Unit, Any>("c_update_location") {
    override val args: Array<out Any?> by lazy {
        Array(1, { locations.transform { it.toJSON() }.toJSONArray() })
    }

    override fun convert(value: Any) {
    }
}

class SendMessageCommand(val msg : Message) : Command<Message, JSONObject>("c_send_message", msg.toJson()) {
    override fun convert(value: JSONObject): Message {
        return value.toMessage()
    }
}

class JoinRoomCommand(roomId : String, fromInvitation: Boolean) : Command<JoinRoomResponse, JSONObject>("c_join_room", roomId, fromInvitation) {
    override fun convert(value: JSONObject): JoinRoomResponse {
        return JoinRoomResponse(value)
    }
}

class LeaveRoomCommand(roomId: String, askOthersToLeave : Boolean) : Command<Unit, Any?>("c_leave_room", roomId, askOthersToLeave) {
    override fun convert(value: Any?) = Unit
}

class RequestMicCommand(roomId : String) : Command<Boolean, Boolean>("c_control_mic", roomId)
class ReleaseMicCommand(roomId: String) : Command<Unit, Any?>("c_release_mic", roomId) {
    override fun convert(value: Any?) = Unit
}

class AddRoomMembersCommand(roomId: String, userIds : Iterable<String>) : Command<Room, JSONObject>("c_add_room_members", roomId, userIds.toJSONArray()) {
    override fun convert(value: JSONObject): Room {
        return RoomObject(value)
    }
}

class InviteRoomMemberCommand(roomId: String) : Command<Int, Int>("c_invite_room_members", roomId)

class ChangePasswordCommand(userId : String, oldPassword : String, newPassword : String) : Command<Unit, Any?>("c_change_pwd", oldPassword, newPassword) {
    override fun convert(value: Any?) = Unit
}


class RoomInvitationObject(obj: JSONObject) : RoomInvitation {
    override val room: Room = RoomObject(obj.getJSONObject("room"))
    override val inviterId: String = obj.getString("inviterId")
    override val inviteTime: Date = Date()
    override val inviterPriority: Int = obj.getInt("inviterPriority")
    override val force: Boolean = obj.getBoolean("force")

    companion object {
        private const val serialVersionUID = 1L
    }
}

class GroupObject(private val obj: JSONObject) : Group {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getStringValue("name")
    override val description: String?
        get() = obj.getStringValue("description")
    override val avatar: String?
        get() = obj.optString("avatar")
    override val memberIds: Collection<String>
        get() = obj.optJSONArray("members").toStringList()

    override fun toString(): String {
        return obj.toString()
    }
}

private fun Message.toJson() : JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("type", type)
        put("roomId", roomId)
        put("body", body)
    }
}

private fun JSONObject.toMessage() : Message {
    return Message(
            id = getString("_id"),
            sendTime = getLong("sendTime"),
            body = getJSONObject("body"),
            type = getString("type"),
            roomId = getString("roomId"),
            read = false,
            senderId = getString("senderId")
    )
}

private class RoomObject(obj: JSONObject) : Room, Serializable {
    override val id: String = obj.getString("idNumber")
    override val name: String = obj.getStringValue("name")
    override val description: String? = obj.getStringValue("description")
    override val ownerId: String = obj.getString("ownerId")
    override val associatedGroupIds: Collection<String> = obj.optJSONArray("associatedGroupIds").toStringList().toList()
    override val extraMemberIds: Collection<String> = obj.optJSONArray("extraMemberIds").toStringList().toList()
}

class UserObject(private val obj: JSONObject) : User {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getStringValue("name")
    override val avatar: String?
        get() = obj.nullOrString("avatar")
    override val permissions: Set<Permission>
        get() = obj.getJSONObject("privileges").toPermissionSet()
    override val priority: Int
        get() = obj.optJSONObject("privileges").optInt("priority", Constants.DEFAULT_USER_PRIORITY)
    override val phoneNumber: String?
        get() = obj.nullOrString("phoneNumber")
    override val enterpriseId: String
        get() = obj.getStringValue("enterId", "")
    override val enterpriseName: String
        get() = obj.getStringValue("enterName", "")
    override val enterpriseExpireDate: Date?
        get() = obj.optLong("enterexpTime", 0).let { if (it <= 0) null else Date(it) }

    val locationEnabled : Boolean
        get() = obj.optBoolean("locationEnable", false)

    val locationScanInterval: Long
        get() = obj.optLong("locationScanInterval", 1L) * 1000

    val locationReportInterval: Long
        get() = obj.optLong("locationReportInterval", 1L) * 1000

    val locationReportWeekDays : SortedSet<DayOfWeek> by lazy {
        val list = obj.optJSONArray("locationWeekly")?.transform { (it as Number).toInt() != 0 } ?: return@lazy ALL_WEEK_DAYS

        list.mapIndexedNotNull { i, b ->
            if (b) {
                DayOfWeek.of(i + 1)
            }
            else {
                null
            }
        }.toSortedSet()
    }
    val locationReportTimeStart : LocalTime =
            obj.optJSONObject("locationTime")?.optString("from", null)?.let { LocalTime.parse(it, Constants.TIME_FORMAT) } ?: LocalTime.MIN

    val locationReportDurationHours : Int =
            obj.optJSONObject("locationTime")?.optInt("last", 24) ?: 24

    val locationScanEnableObservable : Observable<Boolean>
        get() {
            if (locationEnabled.not()) {
                return Observable.just(false)
            }

            return Observable.defer<Boolean> {
                // Calculate the interval time
                val now = ZonedDateTime.now()

                val reportRanges = (-1..1).map { now.plusDays(it.toLong()) }
                        .filter { locationReportWeekDays.contains(it.dayOfWeek) }
                        .map { it.locationReportRange }

                val currRange = reportRanges.firstOrNull { it.contains(now) }
                val enabled = currRange != null
                val nextCheckTime = currRange?.end ?: (reportRanges.firstOrNull { it.start > now }?.start ?: now.plusHours(1))

                logger.i { "Next check location enable time is $nextCheckTime" }
                Observable.timer(Duration.between(now, nextCheckTime).toMillis(), TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .flatMap { Observable.empty<Boolean>() }
                        .startWith(enabled)
            }.repeat()
                    .distinctUntilChanged()
        }

    private val ZonedDateTime.locationReportRange : Range<ZonedDateTime>
    get() = this.with(locationReportTimeStart).let { Range(it, it.plusHours(locationReportDurationHours.toLong())) }

    override fun toString(): String {
        return obj.toString()
    }

    companion object {
        private val ALL_WEEK_DAYS = EnumSet.allOf(DayOfWeek::class.java).toSortedSet()
    }
}


class JoinRoomResponse(private val obj: JSONObject) : JoinRoomResult {

    override val room: Room = RoomObject(obj.getJSONObject("room"))

    override val initiatorUserId: String
        get() = obj.getString("initiatorUserId")

    override val onlineMemberIds: Collection<String>
        get() = obj.getJSONArray("onlineMemberIds").toStringList()

    override val speakerId: String?
        get() = obj.nullOrString("speakerId")

    override val speakerPriority: Int?
        get() = obj.optInt("speakerPriority", -1).let { if (it < 0) null else it }

    override val voiceServerConfiguration: Map<String, Any>
        get() {
            val server = obj.getJSONObject("voiceServer")
            return mapOf(
                    "host" to server.getString("host"),
                    "port" to server.getInt("port"),
                    "protocol" to server.getString("protocol"),
                    "tcpPort" to server.getString("tcpPort")
            )
        }

    override fun toString(): String {
        return obj.toString()
    }
}

private open class RoomSpeakerUpdateObject(protected val obj: JSONObject) : RoomSpeakerUpdate {
    override val roomId: String
        get() = obj.getString("roomId")

    override val speakerId: String?
        get() = obj.nullOrString("speakerId")

    override val speakerPriority: Int?
        get() = obj.optInt("speakerPriority", -1).let { if (it < 0) null else it }
}

private class RoomActiveInfoUpdate(obj: JSONObject) : RoomSpeakerUpdateObject(obj), RoomOnlineMemberUpdate {
    override val memberIds: Collection<String>
        get() = obj.optJSONArray("onlineMemberIds")?.toStringList() ?: emptyList()
}

private fun JSONObject?.toPermissionSet(): Set<Permission> {
    if (this == null) {
        return emptySet()
    }

    val set = EnumSet.noneOf(Permission::class.java)
    if (has("callAble") && getBoolean("callAble")) {
        set.add(Permission.MAKE_INDIVIDUAL_CALL)
    }

    if (has("groupAble") && getBoolean("groupAble")) {
        set.add(Permission.MAKE_TEMPORARY_GROUP_CALL)
    }

    if (has("calledAble") && getBoolean("calledAble")) {
        set.add(Permission.RECEIVE_INDIVIDUAL_CALL)
    }

    if (has("joinAble") && getBoolean("joinAble")) {
        set.add(Permission.RECEIVE_TEMPORARY_GROUP_CALL)
    }

    if (!has("forbidSpeak") || !getBoolean("forbidSpeak")) {
        set.add(Permission.SPEAK)
    }

    if (has("muteAble") && getBoolean("muteAble")) {
        set.add(Permission.MUTE)
    }

    if (has("powerInviteAble") && getBoolean("powerInviteAble")) {
        set.add(Permission.FORCE_INVITE)
    }

    if (has("viewMap") && getBoolean("viewMap")) {
        set.add(Permission.VIEW_MAP)
    }

    return set
}

private fun JSONObject?.toError(): Throwable {
    return if (this != null && has("name")) {
        KnownServerException(getString("name"), getStringValue("message"))
    } else {
        UnknownServerException
    }
}

