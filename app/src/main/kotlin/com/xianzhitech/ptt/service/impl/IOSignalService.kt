package com.xianzhitech.ptt.service.impl

import android.os.Looper
import com.xianzhitech.ptt.Constants
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import rx.Completable
import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.util.*
import java.util.concurrent.atomic.AtomicReference


class IOSignalService(val endpoint: String,
                      val deviceId: String,
                      val okHttpClient: OkHttpClient) : SignalService {
    private val socket = IO.socket(endpoint, IO.Options().apply {
        transports = arrayOf("websocket")
    })

    override fun login(tokenProvider: TokenProvider): Observable<LoginResult> {
        return Observable.create<LoginResult> { subscriber ->
            logd("Logging using $tokenProvider")

            val loginResultRef = AtomicReference<LoginResultObject>(LoginResultObject(status = LoginStatus.LOGIN_IN_PROGRESS, token = tokenProvider.authToken, user = null))

            subscriber.onNext(loginResultRef.get())

            // Set up connection events
            socket.onMainThread(Socket.EVENT_RECONNECTING, {
                loginResultRef.set(loginResultRef.get().copy(status = LoginStatus.LOGIN_IN_PROGRESS))
                subscriber.onNext(loginResultRef.get())
            })

            socket.onMainThread(Socket.EVENT_RECONNECT_FAILED, {
                loginResultRef.set(loginResultRef.get().copy(status = LoginStatus.OFFLINE))
                subscriber.onNext(loginResultRef.get())
            })

            socket.onMainThread(Socket.EVENT_CONNECT_ERROR, {
                loginResultRef.set(loginResultRef.get().copy(status = LoginStatus.OFFLINE))
                subscriber.onNext(loginResultRef.get())
            })

            socket.on(Socket.EVENT_DISCONNECT, {
                logd("Disconnected because of ${it?.joinToString(",")}")
            })


            // Set up server events
            socket.onMainThread("s_login_failed", { subscriber.onError((it as? JSONObject).toError()) })
            socket.onMainThread("s_logon", {
                val userObject = UserObject(it as JSONObject)
                val oldResult = loginResultRef.get()
                loginResultRef.set(oldResult.copy(user = userObject, status = LoginStatus.LOGGED_IN, token = oldResult.token ?: UserToken(userObject.id, tokenProvider.loginPassword!!)))
                subscriber.onNext(loginResultRef.get())
            })
            

            // Process headers
            socket.io().on(Manager.EVENT_TRANSPORT, {
                (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                    try {
                        val (savedToken, loginType) = tokenProvider.authToken?.to("") ?: UserToken(tokenProvider.loginName!!, tokenProvider.loginPassword!!).to(tokenProvider.loginName!!.guessLoginType())
                        if (loginType.isNullOrBlank()) {
                            loginResultRef.set(loginResultRef.get().copy(token = savedToken))
                        }

                        logd("Logging in as ${savedToken.userId}, type: $loginType")

                        val headers = it[0] as MutableMap<String, List<String>>
                        headers["Authorization"] = listOf("Basic ${(savedToken.userId + ':' + savedToken.password.toMD5() + loginType).toBase64()}");
                        headers["X-Device-Id"] = listOf(deviceId)
                    } catch(e: Exception) {
                        subscriber.onError(e)
                    }
                })
            })

            socket.connect()
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    override fun retrieveRoomKickedOutEvent(): Observable<String> {
        return socket.retrieveEvent<Any>("s_kick_out_room").map { it.toString() }
    }

    override fun retrieveInvitation(): Observable<RoomInvitation> {
        return socket.retrieveEvent<JSONObject>("s_invite_to_join")
                .map {
                    RoomInvitationObject(it!!)
                }
    }

    override fun retrieveRoomInfoUpdate(): Observable<Room> {
        return socket.retrieveEvent<JSONObject>("s_member_update").map { RoomObject(it) }
    }

    override fun retrieveUserKickedOutEvent(): Observable<UserKickedOutEvent> {
        return socket.retrieveEvent<Any?>("s_kick_out")
                .map {
                    object : UserKickedOutEvent {
                        override val reason: String?
                            get() = null
                    }
                }
    }

    override fun retrieveRoomOnlineMemberUpdate(): Observable<RoomOnlineMemberUpdate> {
        return socket.retrieveEvent<JSONObject>("s_online_member_update").map { RoomActiveInfoUpdate(it!!) }
    }

    override fun retrieveRoomSpeakerUpdate(): Observable<RoomSpeakerUpdate> {
        return socket.retrieveEvent<JSONObject>("s_speaker_changed").map { RoomSpeakerUpdateObject(it!!) }
    }

    override fun syncContacts(version: Long, userId: String): Single<Contacts> {
        return Single.fromCallable<Contacts> {
            val request = Request.Builder()
                    .get()
                    .url("$endpoint/api/contact/sync/$userId/$version")
                    .build()

            logd("Sync contact old version = $version")

            val result = okHttpClient.newCall(request).execute()
                .use {
                    if (it.code() == 304) {
                        logd("Contact not changed")
                        null
                    }
                    else if (it.code() == 200) {
                        ContactSyncObject(JSONObject(it.body().string()))
                    }
                    else {
                        throw KnownServerException("sync_contact", it.body().string())
                    }
                }

            logd("Sync contact result version = ${result?.version}, groupSize = ${result?.groups?.size}, userSize = ${result?.users?.size}")

            result
        }.subscribeOn(Schedulers.io())
    }

    override fun logout(): Completable {
        return deferComplete {
            // Disconnect socket
            socket.off()
            socket.io().off()
            socket.disconnect()
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

    override fun leaveRoom(roomId: String, askOthersToLeave: Boolean): Completable {
        return socket.sendEventIgnoringResult("c_leave_room", roomId, askOthersToLeave)
    }

    override fun updateRoomMembers(roomId: String, userIds: Iterable<String>): Single<Room> {
        return socket.sendEvent<JSONObject>("c_add_room_members", roomId, userIds.toJSONArray()).map { RoomObject(it) }
    }

    override fun retrieveRoomInfo(roomId: String): Single<Room> {
        //TODO:
        return Single.create { }
    }

    override fun retrieveUserInfo(userId: String): Single<User> {
        throw UnsupportedOperationException()
    }

    override fun changePassword(tokenProvider: TokenProvider, oldPassword: String, newPassword: String): Single<UserToken> {
        return socket.sendEventIgnoringResult("c_change_pwd", oldPassword.toMD5(), newPassword.toMD5()).toSingleDefault(Unit)
                .map {
                    UserToken(userId = tokenProvider.authToken?.userId ?: tokenProvider.loginName!!, password = newPassword)
                }
    }

}

private fun isMainThread(): Boolean {
    return Looper.getMainLooper() == Looper.myLooper()
}


private val phoneMatcher = Regex("^1\\d{10}$")

private fun String.guessLoginType() : String {
    return when {
        contains('@')-> ":MAIL"
        matches(phoneMatcher) -> ":PHONE"
        else -> ""
    }
}

private fun deferComplete(func: () -> Unit): Completable {
    val ret = Completable.defer {
        func()
        Completable.complete()
    }
    if (isMainThread()) {
        return ret
    }

    return ret.subscribeOn(AndroidSchedulers.mainThread())
}

private fun mainThread(func: () -> Unit) {
    deferComplete(func).subscribeSimple()
}

private fun JSONObject?.toError(): Throwable {
    return if (this != null && has("name")) {
        KnownServerException(getString("name"), getStringValue("message"))
    } else {
        UnknownServerException
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Socket.retrieveEvent(eventName: String): Observable<T> {
    return Observable.create<T> { subscriber ->
        val listener: (Array<Any>) -> Unit = {
            logtagd("IOSignalService", "Received $eventName with args ${it.joinToString(",")}")
            subscriber.onNext(it.firstOrNull() as T?)
        }

        on(eventName, listener)
        subscriber.add(Subscriptions.create { off(eventName, listener) })
    }
}

private fun Socket.onMainThread(eventName: String, callback: (Any?) -> Unit) {
    on(eventName, { args ->
        mainThread {
            logtagd("IOSignalService", "Received $eventName with args ${args.joinToString(",")}")
            callback(args.firstOrNull())
        }
    })
}

private fun <T> Socket.sendEventRaw(eventName: String, clazz: Class<T>?, args: Array<out Any?>): Single<T> {
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

private inline fun <reified T : Any> Socket.sendEvent(eventName: String, vararg args: Any?): Single<T> {
    return sendEventRaw(eventName, T::class.java, args)
}

private fun Socket.sendEventIgnoringResult(eventName: String, vararg args: Any?): Completable {
    return Completable.fromSingle(sendEventRaw<Any>(eventName, null, args))
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
        set.add(Permission.MAKE_GROUP_CALL)
    }

    if (has("calledAble") && getBoolean("calledAble")) {
        set.add(Permission.RECEIVE_INDIVIDUAL_CALL)
    }

    if (has("joinAble") && getBoolean("joinAble")) {
        set.add(Permission.RECEIVE_ROOM)
    }

    if (!has("forbidSpeak") || !getBoolean("forbidSpeak")) {
        set.add(Permission.CAN_SPEAK)
    }

    return set
}


private class ContactSyncObject(private val obj: JSONObject) : Contacts {
    override val version: Long
        get() = obj.getLong("version")

    override val groups: Collection<Group>
        get() = obj.optJSONArray("enterpriseGroups")?.transform { GroupObject(it as JSONObject) } ?: emptyList()

    override val users: Collection<User>
        get() = obj.optJSONArray("enterpriseMembers")?.transform { UserObject(it as JSONObject) } ?: emptyList()

    operator fun component1() = users
    operator fun component2() = groups
}


private class JoinRoomResponse(private val obj: JSONObject) : JoinRoomResult {

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

private data class LoginResultObject(override val user: User? = null,
                                     override val token: UserToken? = null,
                                     override val status: LoginStatus = LoginStatus.IDLE) : LoginResult

private class RoomInvitationObject(private @Transient val obj: JSONObject) : RoomInvitation, ExtraRoomInvitation {
    @Transient override val room: Room = RoomObject(obj.getJSONObject("room"))
    override val roomId: String = room.id
    override val inviterId: String = obj.getString("inviterId")
    override val inviteTime: Date = Date()
}

private class GroupObject(private val obj: JSONObject) : Group {
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

private class RoomObject(private val obj: JSONObject) : Room {
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

private class UserObject(private val obj: JSONObject) : User {
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