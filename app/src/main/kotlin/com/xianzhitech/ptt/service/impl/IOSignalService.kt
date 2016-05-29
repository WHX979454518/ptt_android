package com.xianzhitech.ptt.service.impl

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.ui.KickOutActivity
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Completable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.subjects.BehaviorSubject
import java.util.*


class IOSignalService(endpoint : String,
                      private val context: Context,
                      private val userRepository: UserRepository,
                      private val groupRepository: GroupRepository,
                      private val roomRepository: RoomRepository,
                      private val contactRepository: ContactRepository,
                      private val preference: Preference) : SignalService {
    private val socket = IO.socket(endpoint)

    override val roomState = BehaviorSubject.create<RoomState>(RoomState.EMPTY)
    override val loginState = BehaviorSubject.create<LoginState>(LoginState.EMPTY)
    private val errorToastAction = Action1<Throwable> {
        mainThread { Toast.makeText(context, it.describeInHumanMessage(context), Toast.LENGTH_LONG).show() }
    }

    override fun peekRoomState(): RoomState = roomState.value
    override fun peekLoginState(): LoginState = loginState.value

    private var savedUserToken : ExplicitUserToken?
        get() = preference.userSessionToken as? ExplicitUserToken
        set(value) {
            preference.userSessionToken = value
        }

    init {
        savedUserToken?.let {
            logd("Auto login upon non-null user token preference")
            login(it.userId, it.password).doOnError(errorToastAction).subscribeSimple()
        }
    }

    private fun onLoginSuccess(userObject: JSONObject?, password: String) {
        if (userObject == null) {
            onLoginFailed(StaticUserException(R.string.error_service_empty_response))
            return
        }

        val user = UserObject(userObject)
        userRepository.saveUsers(listOf(user)).execAsync()
                .concatWith(Completable.defer {
                    val lastLoginUserId = preference.lastLoginUserId
                    preference.lastLoginUserId = user.id
                    if (lastLoginUserId != null && lastLoginUserId != user.id) {
                        // Clear room information if it's this user's first login
                        logd("Clearing room database because different user has logged in")
                        roomRepository.clear().execAsync()
                                .concatWith(contactRepository.clear().execAsync())
                                .concatWith(groupRepository.clear().execAsync())
                                .concatWith(userRepository.clear().execAsync())
                    } else {
                        Completable.complete()
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { onLoginFailed(it) }
                .subscribeSimple {
                    savedUserToken = ExplicitUserToken(user.id, password)

                    logd("Login success with user $user")
                    loginState += peekLoginState().copy(status = LoginStatus.LOGGED_IN, currentUserID = user.id)

                    doSyncContacts()
                }
    }

    private fun doSyncContacts() {
        logd("Requesting contacts...")
        socket.sendEvent<JSONObject>("c_sync_contact")
                .map {
                    val (users, groups) = ContactSyncObject(it)
                    contactRepository.replaceAllContacts(users, groups).exec()
                }
                .doOnError(errorToastAction)
                .subscribeSimple()
    }

    private fun onLoginFailed(reason: Throwable?) {
        mainThread {
            Toast.makeText(context,
                    R.string.error_login_failed.toFormattedString(context, reason.describeInHumanMessage(context)),
                    Toast.LENGTH_LONG).show()

            logout().subscribeSimple()
        }
    }

    private fun onReconnected() {
        mainThread {
            val state = peekRoomState()
            if (state.status.inRoom) {
                // Rejoined the room upon reconnection
                logd("Rejoining room ${state.currentRoomId}")
                joinRoom(state.currentRoomId!!)
                        .doOnError(errorToastAction)
                        .subscribeSimple()
            }
        }
    }

    private fun onReconnectFailed() {
        mainThread {
            val state = peekLoginState()
            if (state.status != LoginStatus.IDLE) {
                logd("Reconnected failed")
                loginState += state.copy(status = LoginStatus.OFFLINE)
            }
        }
    }

    private fun onReconnecting() {
        mainThread {
            loginState += peekLoginState().copy(status = LoginStatus.LOGIN_IN_PROGRESS)
        }
    }

    override fun login(loginName: String, password: String): Completable {
        return deferComplete {
            if (peekLoginState().status != LoginStatus.IDLE) {
                throw IllegalStateException("Already logged on")
            }

            logd("Logging in user $loginName")

            // Set up connection events
            socket.on(Socket.EVENT_RECONNECTING, { onReconnecting() })
            socket.on(Socket.EVENT_RECONNECT, { onReconnected() })
            socket.on(Socket.EVENT_RECONNECT_FAILED, { onReconnectFailed() })
            socket.on(Socket.EVENT_CONNECT_ERROR, { onConnectError() })

            // Set up server events
            socket.on("s_login_failed", { onLoginFailed((it.firstOrNull() as? JSONObject).toError()) })
            socket.on("s_logon", { onLoginSuccess(it.firstOrNull() as? JSONObject, savedUserToken?.password ?: password) })
            socket.on("s_online_member_update", { onRoomActiveInfoUpdate(it.firstOrNull() as? JSONObject) })
            socket.on("s_speaker_changed", { onSpeakerChanged(it.firstOrNull() as? JSONObject) })
            socket.on("s_invite_to_join", { onInviteToJoin(it.firstOrNull() as? JSONObject) })
            socket.on("s_kick_out", { onUserKickedOut() })

            loginState += peekLoginState().copy(status = LoginStatus.LOGIN_IN_PROGRESS, currentUserID = savedUserToken?.userId)

            // Process headers
            socket.io().on(Manager.EVENT_TRANSPORT, {
                (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, {
                    val savedToken = savedUserToken
                    val authName = savedToken?.userId ?: loginName
                    val authPassword = savedToken?.password ?: password

                    (it[0] as MutableMap<String, List<String>>)["Authorization"] =
                            listOf("Basic ${(authName + ':' + authPassword.toMD5()).toBase64()}");
                })
            })

            socket.connect()
        }
    }

    private fun onUserKickedOut() {
        mainThread {
            if (currentUserId != null) {
                logout().subscribeSimple()
                context.startActivity(Intent(context, KickOutActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
    }

    private fun onInviteToJoin(inviteObject: JSONObject?) {
        if (inviteObject != null) {
            val invitation = RoomInvitationObject(inviteObject)

            roomRepository.saveRooms(listOf(invitation.room)).execAsync()
                .subscribeSimple {
                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(SignalService.ACTION_INVITE_TO_JOIN)
                            .putExtra(SignalService.EXTRA_INVITE, invitation))
                }
        }
    }

    private fun onConnectError() {
        mainThread {
            val currLoginState = peekLoginState()
            if (currLoginState.currentUserID != null) {
                loginState += currLoginState.copy(status = LoginStatus.OFFLINE)
            }
        }
    }

    private fun onSpeakerChanged(data: JSONObject?) {
        mainThread {
            if (data != null) {
                logd("Room speaker changed: $data")
                val speakerUpdate = RoomSpeakerUpdate(data)
                val currRoomState = peekRoomState()
                if (currRoomState.currentRoomId == speakerUpdate.roomId) {
                    val newStatus = if (speakerUpdate.speakerId == null) {
                        RoomStatus.JOINED
                    } else {
                        RoomStatus.ACTIVE
                    }

                    roomState += currRoomState.copy(speakerId = speakerUpdate.speakerId, status = newStatus)
                }
            }
        }
    }

    private fun onRoomActiveInfoUpdate(data: JSONObject?) {
        mainThread {
            if (data != null) {
                logd("Room updated with data : $data")
                val roomInfo = RoomActiveInfoUpdate(data)
                val currRoomState = peekRoomState()
                if (currRoomState.currentRoomId == roomInfo.roomId) {
                    roomState += currRoomState.copy(onlineMemberIds = roomInfo.onlineMemberIds.toSet(), speakerId = roomInfo.speakerId)
                }
            }
        }
    }

    override fun logout(): Completable {
        return deferComplete {
            // Disconnect socket
            socket.off()
            socket.io().off()
            socket.disconnect()

            // Clear room state
            if (peekRoomState().status != RoomStatus.IDLE) {
                roomState += RoomState.EMPTY
            }

            // Clear login state
            if (peekLoginState().status != LoginStatus.IDLE) {
                loginState += LoginState.EMPTY
            }

            savedUserToken = null
        }
    }

    override fun createRoom(request: CreateRoomRequest): Single<Room> {
        return deferFlatSingle {
            socket.sendEvent<JSONObject>("c_create_room", request.name, request.groupIds.toJSONArray(), request.extraMemberIds.toJSONArray())
                    .map {
                        val room : Room = RoomObject(it)
                        roomRepository.saveRooms(listOf(room)).exec()
                        room
                    }
        }
    }

    override fun joinRoom(roomId: String): Completable {
        return Completable.fromSingle(deferFlatSingle {
            val currRoomState = peekRoomState()
            if (currRoomState.currentRoomId == roomId) {
                return@deferFlatSingle Single.just(Unit)
            }

            if (currRoomState.currentRoomId != null) {
                leaveRoom().subscribeSimple()
            }

            roomState += currRoomState.copy(
                    status = RoomStatus.JOINING,
                    currentRoomId = roomId,
                    speakerId = null,
                    voiceServer = emptyMap(),
                    onlineMemberIds = emptySet())

            socket.sendEvent<JSONObject>("c_join_room", roomId)
                    .map {
                        val response = JoinRoomResponse(it)
                        roomRepository.saveRooms(listOf(response.room)).exec()
                        onRoomJoined(response)
                    }
                    .doOnError { onRoomJoinFailed(roomId, it) }
        })
    }

    private fun onRoomJoinFailed(roomId: String, reason: Throwable?) {
        logd("Joining room $roomId failed because: $reason")
        leaveRoom().subscribeSimple()
    }


    private fun onRoomJoined(response: JoinRoomResponse) {
        mainThread {
            logd("Joined room with response $response")
            val newRoomStatus = if (response.speakerId == null) {
                RoomStatus.JOINED
            } else {
                RoomStatus.ACTIVE
            }

            roomState += peekRoomState().copy(
                    status = newRoomStatus,
                    speakerId = response.speakerId,
                    onlineMemberIds = response.onlineMemberIds.toSet(),
                    voiceServer = response.serverConfiguration)
        }
    }

    override fun requestMic(): Single<Boolean> {
        return deferFlatSingle {
            val currRoomState = peekRoomState()
            val currLoginState = peekLoginState()

            if (currRoomState.status.inRoom.not() || currRoomState.currentRoomId == null) {
                logd("No room joined currently")
                return@deferFlatSingle Single.just(false)
            }

            if (currRoomState.speakerId == currLoginState.currentUserID) {
                logd("User already has mic")
                return@deferFlatSingle Single.just(true)
            }

            if (currRoomState.speakerId != null) {
                return@deferFlatSingle Single.just(false)
            }

            roomState += currRoomState.copy(status = RoomStatus.REQUESTING_MIC)
            socket.sendEvent<Boolean>("c_control_mic", currRoomState.currentRoomId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess { onRequestMicFinished(currLoginState, currRoomState, it) }
                    .doOnError { onRequestMicError(currRoomState, it) }
                    .onErrorReturn {
                        logd("Error requesting mic: $it")
                        false
                    }
        }
    }

    private fun onRequestMicError(prevRoomState: RoomState, throwable: Throwable?) {
        mainThread {
            logd("Error requesting mic: $throwable")
            val newRoomState = peekRoomState()
            if (newRoomState.currentRoomId == prevRoomState.currentRoomId) {
                releaseMic().subscribeSimple()
            }
        }
    }

    private fun onRequestMicFinished(prevLoginState: LoginState, prevRoomState: RoomState, hasMic : Boolean) {
        mainThread {
            val newRoomState = peekRoomState()
            if (newRoomState.currentRoomId == prevRoomState.currentRoomId &&
                    newRoomState.speakerId == null && hasMic &&
                    newRoomState.status == RoomStatus.REQUESTING_MIC) {
                logd("Request mic finished with result: $hasMic")
                val newStatus = if (hasMic) RoomStatus.ACTIVE else RoomStatus.JOINED
                roomState += newRoomState.copy(speakerId = prevLoginState.currentUserID, status = newStatus)
            }
        }
    }

    override fun releaseMic(): Completable {
        return deferFlatComplete {
            val currRoomState = peekRoomState()
            val currLoginState = peekLoginState()

            if (currRoomState.speakerId == null || currRoomState.speakerId == currLoginState.currentUserID) {
                roomState += currRoomState.copy(status = RoomStatus.JOINED, speakerId = null)

                if (currRoomState.speakerId == currLoginState.currentUserID) {
                    return@deferFlatComplete socket.sendEventIgnoringResult("c_release_mic", currRoomState.currentRoomId)
                }
            }

            Completable.complete()
        }
    }

    override fun leaveRoom(): Completable {
        return deferFlatComplete {
            val currRoomState = peekRoomState()
            if (currRoomState.status.inRoom.not()) {
                return@deferFlatComplete Completable.complete()
            }

            socket.sendEventIgnoringResult("c_leave_room", currRoomState.currentRoomId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnCompleted {
                        val newRoomId = peekRoomState().currentRoomId
                        if (newRoomId == null || newRoomId == currRoomState.currentRoomId) {
                            roomState += RoomState.EMPTY
                        }
                    }
        }
    }

    override fun updateRoomMembers(roomId: String, userIds: Iterable<String>): Completable {
        return Completable.fromSingle(deferFlatSingle {
            socket.sendEvent<JSONObject>("c_add_room_members", roomId, userIds.toJSONArray())
                    .map { roomRepository.saveRooms(listOf(RoomObject(it))).exec() }
        })
    }

    override fun retrieveRoomInfo(roomId: String): Single<Room> {
        //TODO:
        return Single.create {  }
    }

    override fun retrieveUserInfo(userId: String): Single<User> {
        throw UnsupportedOperationException()
    }

    override fun changePassword(oldPassword: String, newPassword: String): Completable {
        return deferFlatComplete {
            val currLoginState = peekLoginState()
            if (currLoginState.currentUserID == null) {
                throw IllegalStateException("User not logon")
            }

            socket.sendEventIgnoringResult("c_change_pwd", oldPassword.toMD5(), newPassword.toMD5())
                    .doOnCompleted {
                        val newLoginState = peekLoginState()
                        if (newLoginState.currentUserID == newLoginState.currentUserID) {
                            savedUserToken = ExplicitUserToken(currLoginState.currentUserID, newPassword)
                        }

                    }
        }
    }

    private data class ExplicitUserToken(override val userId : String,
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

private fun deferFlatComplete(func: () -> Completable) : Completable {
    val ret = Completable.defer { func() }
    if (isMainThread()) {
        return ret
    }

    return ret.subscribeOn(AndroidSchedulers.mainThread())
}


private fun <T> deferFlatSingle(func: () -> Single<T>) : Single<T> {
    val ret = Single.defer<T> { func() }

    if (isMainThread()) {
        return ret
    }

    return ret.subscribeOn(AndroidSchedulers.mainThread())
}

private fun <T> deferSingle(func: () -> T?) : Single<T> {
    val ret = Single.defer<T> {
        Single.just(func())
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

private fun <T> Socket.sendEventRaw(eventName: String, clazz : Class<T>?, args: Array<out Any?>) : Single<T> {
    return Single.create { subscriber ->
        val ack = Ack {
            logd("$eventName received ${it.joinToString(",")}")

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

        logd("Sending $eventName(${args.joinToString(",")})")
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

private class ContactSyncObject(private val obj : JSONObject) {
    val groups : Iterable<Group>
        get() = obj.optJSONObject("enterpriseGroups")?.optJSONArray("add")?.transform { GroupObject(it as JSONObject) } ?: emptyList()

    val users : Iterable<User>
        get() =  obj.optJSONObject("enterpriseMembers")?.optJSONArray("add")?.transform { UserObject(it as JSONObject) } ?: emptyList()

    operator fun component1() = users
    operator fun component2() = groups
}


private class JoinRoomResponse(private val obj : JSONObject) {

    val room : Room = RoomObject(obj.getJSONObject("room"))

    val onlineMemberIds: Iterable<String>
        get() = obj.getJSONArray("onlineMemberIds").toStringList()

    val speakerId: String?
        get() = obj.nullOrString("speakerId")

    val serverConfiguration : Map<String, Any?>
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

private open class RoomSpeakerUpdate(protected val obj : JSONObject) {
    val roomId : String
        get() = obj.getString("roomId")

    val speakerId : String?
        get() = obj.nullOrString("speakerId")
}

private class RoomActiveInfoUpdate(obj : JSONObject) : RoomSpeakerUpdate(obj) {
    val onlineMemberIds : Iterable<String>
        get() = obj.optJSONArray("onlineMemberIds")?.toStringList() ?: emptyList()
}

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