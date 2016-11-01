package com.xianzhitech.ptt.service

import android.net.Uri
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.dto.JoinRoomResult
import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
import io.socket.client.Ack
import io.socket.client.IO.Options
import io.socket.client.IO.socket
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.Transport
import org.json.JSONObject
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Single
import rx.functions.Func1
import rx.lang.kotlin.add
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.*

private val logger = LoggerFactory.getLogger("SignalService")

fun receiveSignal(uri: Uri,
                  signalFactory: SignalFactory,
                  retryPolicy : RetryPolicy,
                  loginStatusNotification: (status : LoginStatus) -> Unit,
                  connectivityProvider : ConnectivityProvider,
                  commandEmitter: Observable<Command<*, in Any>>,
                  deviceIdProvider : DeviceIdFactory,
                  loginTimeoutProvider : () -> Long,
                  authTokenFactory: AuthTokenFactory) : Observable<Signal> {

    return Observable.create<Signal> { subscriber ->
        val s = socket(uri.toString(), Options().apply {
            multiplex = false
            reconnection = false
            transports = arrayOf("websocket")
            timeout = loginTimeoutProvider()
        })

        s.on(Socket.EVENT_CONNECT, {
            logger.i { "Connected to $uri" }
        })

        s.on(Socket.EVENT_CONNECTING, {
            logger.i { "Connecting to $uri" }
            loginStatusNotification(LoginStatus.LOGIN_IN_PROGRESS)
        })

        val errorListener = Emitter.Listener {
            val err = it.firstOrNull() as? Throwable
            logger.e { "Connect error: $err" }
            loginStatusNotification(LoginStatus.IDLE)
            subscriber.onError(err)
        }

        s.io().on(Manager.EVENT_PING, { logger.i { "Ping!" } })
        s.io().on(Manager.EVENT_PONG, { logger.i { "Pong!" } })

        s.io().on(Manager.EVENT_TRANSPORT, {
            val transport = it.first() as Transport
            transport.on(Transport.EVENT_REQUEST_HEADERS, {
                val headers = it.first() as MutableMap<String, List<String>>
                headers["Authorization"] = listOf(authTokenFactory.authToken)
                headers["X-Device-Id"] = listOf(deviceIdProvider.deviceId)
            })
        })

        s.on(Socket.EVENT_CONNECT_ERROR, errorListener)
        s.on(Socket.EVENT_ERROR, errorListener)

        s.on(Socket.EVENT_DISCONNECT, {
            if (subscriber.isUnsubscribed.not()) {
                loginStatusNotification(LoginStatus.IDLE)
                subscriber.onError(InterruptedException())
            }
        })

        s.on("s_login_failed", {
            val err = (it.firstOrNull() as? JSONObject).toError()
            logger.e(err) { "User login failed because $err" }
            subscriber.onError(err)
        })

        s.on("s_logon", {
            if (subscriber.isUnsubscribed) {
                return@on
            }

            val user = UserObject(it[0] as JSONObject)
            logger.i { "User logon as $user" }
            retryPolicy.notifySuccess()
            loginStatusNotification(LoginStatus.LOGGED_IN)
            subscriber += UserLoggedInSignal(user)

            subscriber.add(commandEmitter.subscribe { cmd ->
                logger.d { "Sending command $cmd" }
                s.emit(cmd.cmd, *cmd.args, Ack {
                    logger.d { "Received ${cmd.cmd} result: ${it.print()}" }
                    try {
                        val resultObj = it.first() as JSONObject
                        if (resultObj.optBoolean("success", false)) {
                            if (resultObj.has("data")) {
                                cmd.resultSubject.onNext(resultObj.get("data"))
                            }
                            else {
                                cmd.resultSubject.onNext(Unit)
                            }
                        } else {
                            cmd.resultSubject.onError(resultObj.getJSONObject("error").toError())
                        }
                    } catch(e: Exception) {
                        cmd.resultSubject.onError(e)
                    }
                })
            })

            signalFactory.signalNames.forEach { name ->
                s.on(name, {
                    val signal = signalFactory.createSignal(name, *it)
                    if (signal == null) {
                        logger.e { "Unrecognized signal $name" }
                    }
                    else {
                        subscriber.onNext(signal)
                    }
                })
            }

            subscriber.add(connectivityProvider.connected.subscribe {
                logger.i { "Connectivity changed to $it" }
                if (it.not()) {
                    loginStatusNotification(LoginStatus.IDLE)
                    subscriber.onError(RuntimeException("No internet"))
                }
            })
        })

        subscriber.add {
            logger.i { "Disconnecting socket $uri" }
            s.off()
            s.io().off()
            s.close()
        }

        loginStatusNotification(LoginStatus.LOGIN_IN_PROGRESS)
        s.connect()
    }.retryWhen {
        it.switchMap<Any>(Func1 { err ->
            if (err is KnownServerException || retryPolicy.canContinue(err).not()) {
                Observable.error(err)
            }
            else {
                retryPolicy.scheduleNextRetry()
            }
        })
    }
}

interface DeviceIdFactory {
    val deviceId : String
}

interface AuthTokenFactory {
    val authToken : String
}

interface ConnectivityProvider {
    val connected : Observable<Boolean>
}

interface Signal

interface SignalFactory {
    fun createSignal(name : String, vararg obj : Any?) : Signal?
    val signalNames : List<String>
}

data class UserLoggedInSignal(val user : UserObject) : Signal
data class UserKickOutSignal(val reason : String? = null) : Signal
data class RoomInviteSignal(val invitation: RoomInvitation) : Signal
data class RoomUpdateSignal(val room : Room) : Signal
data class RoomOnlineMemberUpdateSignal(val update : RoomOnlineMemberUpdate) : Signal
data class RoomSpeakerUpdateSignal(val update : RoomSpeakerUpdate) : Signal
data class RoomKickOutSignal(val roomId: String) : Signal
data class UserUpdatedSignal(val user : UserObject) : Signal


class DefaultSignalFactory : SignalFactory {
    companion object {
        private val allSignals = listOf<Pair<String, (Array<out Any?>) -> Signal>>(
                "s_kick_out" to { obj -> UserKickOutSignal() },
                "s_invite_to_join" to { obj -> RoomInviteSignal(RoomInvitationObject(obj.first() as JSONObject)) } ,
                "s_member_update" to { obj -> RoomUpdateSignal(RoomObject(obj.first() as JSONObject)) } ,
                "s_online_member_update" to { obj -> RoomOnlineMemberUpdateSignal(RoomActiveInfoUpdate(obj.first() as JSONObject)) } ,
                "s_speaker_changed" to { obj -> RoomSpeakerUpdateSignal(RoomSpeakerUpdateObject(obj.first() as JSONObject)) } ,
                "s_kick_out_room" to { obj -> RoomKickOutSignal(obj.first().toString()) },
                "s_user_updated" to { args -> UserUpdatedSignal(UserObject(args.first() as JSONObject)) }
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
                         vararg val args: Any?)  {
    val resultSubject : BehaviorSubject<V> = BehaviorSubject.create()

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

class ChangePasswordCommand(userId : String, oldPassword : String, newPassword : String) : Command<Unit, Any?>("c_change_pwd", oldPassword, newPassword) {
    override fun convert(value: Any?) = Unit
}


class RoomInvitationObject(obj: JSONObject) : RoomInvitation {
    override val room: Room = RoomObject(obj.getJSONObject("room"))
    override val inviterId: String = obj.getString("inviterId")
    override val inviteTime: Date = Date()
    override val inviterPriority: Int = obj.getInt("inviterPriority")
    override val force: Boolean = obj.getBoolean("force")
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
        get() = obj.getJSONObject("privileges").optInt("priority", Constants.DEFAULT_USER_PRIORITY)
    override val phoneNumber: String?
        get() = obj.nullOrString("phoneNumber")
    override val enterpriseId: String
        get() = obj.getStringValue("enterId", "")
    override val enterpriseName: String
        get() = obj.getStringValue("enterName", "")
    override val enterpriseExpireDate: Date?
        get() = obj.optLong("enterexpTime", 0).let { if (it <= 0) null else Date(it) }

    override fun toString(): String {
        return obj.toString()
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

    return set
}

private fun JSONObject?.toError(): Throwable {
    return if (this != null && has("name")) {
        KnownServerException(getString("name"), getStringValue("message"))
    } else {
        UnknownServerException
    }
}

