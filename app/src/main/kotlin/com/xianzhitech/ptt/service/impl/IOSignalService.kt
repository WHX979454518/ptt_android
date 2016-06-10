package com.xianzhitech.ptt.service.impl

import android.os.Looper
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.dto.*
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Completable
import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.Subscriptions
import java.util.*


class IOSignalService(val endpoint : String) : SignalService {
    private val socket = IO.socket(endpoint)
    private var currUserToken : ExplicitUserToken? = null

    override fun login(loginName: String, password: String): Observable<LoginResult> {
        return Observable.create<LoginResult> { subscriber ->
            logd("Logging in user $loginName")

            // Just in case someone called us before
            socket.off()

            var loginResult = LoginResultObject(status = LoginStatus.LOGIN_IN_PROGRESS, token = currUserToken, user = null)

            subscriber.onNext(loginResult)

            // Set up connection events
            socket.onMainThread(Socket.EVENT_RECONNECTING, {
                loginResult = loginResult.copy(status = LoginStatus.LOGIN_IN_PROGRESS)
                subscriber.onNext(loginResult)
            })

            socket.onMainThread(Socket.EVENT_RECONNECT_FAILED, {
                loginResult = loginResult.copy(status = LoginStatus.OFFLINE)
                subscriber.onNext(loginResult)
            })

            socket.onMainThread(Socket.EVENT_CONNECT_ERROR, {
                subscriber.onError(StaticUserException(R.string.error_unknown))
            })

            // Set up server events
            socket.onMainThread("s_login_failed", { subscriber.onError((it as? JSONObject).toError()) })
            socket.onMainThread("s_logon", {
                val user = UserObject(it as JSONObject)
                if (currUserToken == null) {
                    currUserToken = ExplicitUserToken(user.id, password)
                }
                loginResult = loginResult.copy(token = currUserToken, user = user)
                subscriber.onNext(loginResult)
            })

            // Process headers
            socket.io().on(Manager.EVENT_TRANSPORT, {
                (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                    val savedToken = currUserToken
                    val authName = savedToken?.userId ?: loginName
                    val authPassword = savedToken?.password ?: password

                    (it[0] as MutableMap<String, List<String>>)["Authorization"] =
                            listOf("Basic ${(authName + ':' + authPassword.toMD5()).toBase64()}");
                })
            })

            socket.connect()

            subscriber.add(Subscriptions.create {
                socket.off()
            })
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    override fun retrieveInvitation(): Observable<RoomInvitation> {
        return socket.retrieveEvent<JSONObject>("s_invite_to_join")
                .map { RoomInvitationObject(it!!) }
    }

    override fun retrieveUserKickedOutEvent(): Observable<UserKickedOutEvent> {
        //TODO:
        return socket.retrieveEvent<Unit>("s_kicked_out")
                .map { null }
    }

    override fun retrieveRoomOnlineMemberUpdate(): Observable<RoomOnlineMemberUpdate> {
        return socket.retrieveEvent<JSONObject>("s_room_active_member_update").map { RoomActiveInfoUpdate(it!!) }
    }

    override fun retrieveRoomSpeakerUpdate(): Observable<RoomSpeakerUpdate> {
        return socket.retrieveEvent<JSONObject>("s_room_speaker_changed").map { RoomSpeakerUpdateObject(it!!) }
    }

    override fun retrieveContacts(): Single<Contacts> {
        return socket.sendEvent<JSONObject>("c_sync_contact").map { ContactSyncObject(it!!) }
    }

    override fun logout(): Completable {
        return deferComplete {
            // Disconnect socket
            socket.off()
            socket.io().off()
            socket.disconnect()

            currUserToken = null
        }
    }

    override fun createRoom(request: CreateRoomRequest): Single<Room> {
        return socket.sendEvent<JSONObject>("c_create_room", request.name, request.groupIds.toJSONArray(), request.extraMemberIds.toJSONArray())
                .map { RoomObject(it!!) }
    }

    override fun joinRoom(roomId: String): Single<JoinRoomResult> {
        return socket.sendEvent<JSONObject>("c_join_room", roomId).map { JoinRoomResponse(it) }
    }

    override fun requestMic(roomId: String): Single<Boolean> {
        return socket.sendEvent<Boolean>("c_control_mic", roomId)
    }

    override fun releaseMic(roomId: String): Completable {
        return socket.sendEventIgnoringResult("c_release_mic", roomId)
    }

    override fun leaveRoom(roomId: String): Completable {
        return socket.sendEventIgnoringResult("c_leave_room", roomId)
    }

    override fun updateRoomMembers(roomId: String, userIds: Iterable<String>): Single<Room> {
        return socket.sendEvent<JSONObject>("c_add_room_members", roomId, userIds.toJSONArray()).map { RoomObject(it) }
    }

    override fun retrieveRoomInfo(roomId: String): Single<Room> {
        //TODO:
        return Single.create {  }
    }

    override fun retrieveUserInfo(userId: String): Single<User> {
        throw UnsupportedOperationException()
    }

    override fun changePassword(oldPassword: String, newPassword: String): Single<UserToken> {
        return socket.sendEvent<Unit>("c_change_pwd", oldPassword.toMD5(), newPassword.toMD5())
                .observeOn(AndroidSchedulers.mainThread())
                .map {
                    currUserToken = currUserToken?.copy(password = newPassword) ?: throw IllegalStateException("User not logged in")
                    currUserToken!!
                }
    }

    private data class ExplicitUserToken(val userId : String,
                                         val password : String) : UserToken
}


private fun isMainThread() : Boolean {
    return Looper.getMainLooper() == Looper.myLooper()
}

private fun deferComplete(func: () -> Unit) : Completable {
    val ret = Completable.defer {
        func()
        Completable.complete()
    }
    if (isMainThread()) {
        return ret
    }

    return ret.subscribeOn(AndroidSchedulers.mainThread())
}

private fun mainThread(func : () -> Unit) {
    deferComplete(func).subscribeSimple()
}

private fun JSONObject?.toError() : Throwable {
    return if (this != null && has("name")) {
        KnownServerException(getString("name"), getStringValue("message"))
    } else {
        UnknownServerException
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Socket.retrieveEvent(eventName: String) : Observable<T?> {
    return Observable.create<T> { subscriber ->
        val listener: (Array<Any>) -> Unit = {
            logtagd("IOSignalService", "Received $eventName with args ${it.joinToString(",")}")
            subscriber.onNext(it.firstOrNull() as T?)
        }

        on(eventName, listener)
        subscriber.add(Subscriptions.create { off(eventName, listener) })
    }
}

private fun Socket.onMainThread(eventName: String, callback : (Any?) -> Unit) {
    on(eventName, { args ->
        mainThread {
            logtagd("IOSignalService", "Received $eventName with args ${args.joinToString(",")}")
            callback(args.firstOrNull())
        }
    })
}

private fun <T> Socket.sendEventRaw(eventName: String, clazz : Class<T>?, args: Array<out Any?>) : Single<T> {
    return Single.create { subscriber ->
        val ack = Ack {
            logtagd("IOSignalService", "$eventName received ${it.joinToString(",")}")

            if (subscriber.isUnsubscribed) {
                return@Ack
            }

            try {
                val result = it.first() as JSONObject
                if (result.getBoolean("success")) {
                    subscriber.onSuccess(clazz?.cast(result.opt("data")));
                } else {
                    subscriber.onError(result.optJSONObject("error").toError())
                }

            } catch(e: Exception) {
                subscriber.onError(e)
            }
        }

        logtagd("IOSignalService", "Sending $eventName(${args.joinToString(",")})")
        emit(eventName, args, ack)
    }
}

private inline fun <reified T : Any> Socket.sendEvent(eventName: String, vararg args : Any?) : Single<T> {
    return sendEventRaw(eventName, T::class.java, args)
}

private fun Socket.sendEventIgnoringResult(eventName: String, vararg args : Any?) : Completable {
    return Completable.fromSingle(sendEventRaw<Any>(eventName, null, args))
}

private fun String?.toPermissionSet() : Set<Permission> {
    if (this == null) {
        return emptySet()
    }

    //TODO:
    return emptySet()
}

private class ContactSyncObject(private val obj : JSONObject) : Contacts {
    override val groups : Collection<Group>
        get() = obj.optJSONObject("enterpriseGroups")?.optJSONArray("add")?.transform { GroupObject(it as JSONObject) } ?: emptyList()

    override val users : Collection<User>
        get() =  obj.optJSONObject("enterpriseMembers")?.optJSONArray("add")?.transform { UserObject(it as JSONObject) } ?: emptyList()

    operator fun component1() = users
    operator fun component2() = groups
}


private class JoinRoomResponse(private val obj : JSONObject) : JoinRoomResult {

    override val room : Room = RoomObject(obj.getJSONObject("room"))

    override val onlineMemberIds: Collection<String>
        get() = obj.getJSONArray("onlineMemberIds").toStringList()

    override val speakerId: String?
        get() = obj.nullOrString("speakerId")

    override val voiceServerConfiguration : Map<String, Any>
        get() {
            val server = obj.getJSONObject("voiceServer")
            return mapOf(
                    "host" to server.getString("host"),
                    "port" to server.getInt("port"),
                    "protocol" to server.getString("protocol")
            )
        }

    override fun toString(): String {
        return obj.toString()
    }
}

private open class RoomSpeakerUpdateObject(protected val obj : JSONObject) : RoomSpeakerUpdate {
    override val roomId : String
        get() = obj.getString("roomId")

    override val speakerId : String?
        get() = obj.nullOrString("speakerId")
}

private class RoomActiveInfoUpdate(obj : JSONObject) : RoomSpeakerUpdateObject(obj), RoomOnlineMemberUpdate {
    override val memberIds : Collection<String>
        get() = obj.optJSONArray("onlineMemberIds")?.toStringList() ?: emptyList()
}

private data class LoginResultObject(override val user: User? = null,
                                     override val token: UserToken? = null,
                                     override val status: LoginStatus = LoginStatus.IDLE) : LoginResult

private class RoomInvitationObject(private @Transient val obj : JSONObject) : RoomInvitation {
    @Transient val room : Room = RoomObject(obj.getJSONObject("room"))

    override val roomId: String = room.id
    override val inviterId: String = obj.getString("inviterId")
    override val inviteTime: Date = Date()
}

private class GroupObject(private val obj : JSONObject) : Group {
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

private class RoomObject(private val obj : JSONObject) : Room {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getStringValue("name")
    override val description: String?
        get() = obj.getStringValue("description")
    override val ownerId: String
        get() = obj.getString("ownerId")
    override val associatedGroupIds: Collection<String>
        get() = obj.optJSONArray("associatedGroupIds").toStringList()
    override val extraMemberIds: Collection<String>
        get() = obj.optJSONArray("extraMemberIds").toStringList()

    override fun toString(): String {
        return obj.toString()
    }
}

private class UserObject(private val obj : JSONObject) : User {
    override val id: String
        get() = obj.getString("idNumber")
    override val name: String
        get() = obj.getStringValue("name")
    override val avatar: String?
        get() = obj.nullOrString("avatar")
    override val permissions: Set<Permission>
        get() = obj.getStringValue("privileges").toPermissionSet()
    override val priority: Int
        get() = obj.optInt("priority", Constants.DEFAULT_USER_PRIORITY)
    override val phoneNumber: String?
        get() = obj.nullOrString("phoneNumber")
    override val enterpriseId: String
        get() = obj.getStringValue("enterpriseId", "") // TODO:
    override val enterpriseName: String
        get() = obj.getStringValue("enterpriseName", "TODO: 企业名称")

    override fun toString(): String {
        return obj.toString()
    }
}