package com.xianzhitech.ptt.service.provider

import android.support.v4.util.ArrayMap
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.InvalidSavedTokenException
import com.xianzhitech.ptt.service.ServerException
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import rx.subjects.PublishSubject
import rx.subscriptions.Subscriptions
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.collections.*

/**
 *
 * 用Socket.io写的一个服务器
 *
 * Created by fanchao on 17/12/15.
 */
class SocketIOProvider(private val broker: Broker, private val endpoint: String) : AbstractSignalProvider(), AuthProvider {
    companion object {
        public const val EVENT_SERVER_USER_LOGON = "s_logon"
        public const val EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED = "s_member_update"
        public const val EVENT_SERVER_SPEAKER_CHANGED = "s_speaker_changed"
        public const val EVENT_SERVER_ROOM_INFO_CHANGED = "s_room_summary"

        public const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        public const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
        public const val EVENT_CLIENT_JOIN_ROOM = "c_join_room"
        public const val EVENT_CLIENT_LEAVE_ROOM = "c_leave_room"
        public const val EVENT_CLIENT_CONTROL_MIC = "c_control_mic"
        public const val EVENT_CLIENT_RELEASE_MIC = "c_release_mic"
    }

    private lateinit var socket: Socket
    private val logonUser = AtomicReference<Person>()
    private val errorSubject = PublishSubject.create<Any>()

    override fun createConversation(request: CreateConversationRequest): Observable<Conversation> {
        return socket
                .sendEvent(EVENT_CLIENT_CREATE_ROOM, { it[0] as JSONObject }, request.toJSON())
                .flatMap {
                    val conversation = Conversation().readFrom(it)

                    broker.saveConversation(conversation)
                            .concatWith(broker.updateConversationMembers(conversation.id, it.getJSONArray("members").toStringIterable()).map { conversation })
                }
    }

    private fun <T> reifiedErrorSubject(): PublishSubject<T> = (errorSubject as PublishSubject<T>)

    override fun deleteConversation(conversationId: String): Observable<Unit> {
        return Observable.empty()
    }

    override fun peekCurrentLogonUserId() = logonUser.get()?.id

    override fun joinConversation(conversationId: String): Observable<RoomInfo> {
        return socket.sendEvent(
                EVENT_CLIENT_JOIN_ROOM,
                { (it[0] as JSONObject).toRoomInfo(conversationId, peekCurrentLogonUserId() ?: throw IllegalStateException("Not logon")) },
                JSONObject().put("roomId", conversationId))
                .flatMap { roomInfo ->
                    ensureActiveMemberSubject(conversationId).onNext(roomInfo.activeMembers)
                    ensureCurrentSpeakerSubject(conversationId).onNext(roomInfo.speaker)
                    broker.updateConversationMembers(conversationId, roomInfo.members).map { roomInfo }
                }
                .mergeWith(reifiedErrorSubject())
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        return socket.sendEvent(EVENT_CLIENT_LEAVE_ROOM,
                {}, JSONObject().put("roomId", conversationId))
                .mergeWith(reifiedErrorSubject())
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return socket.sendEvent(EVENT_CLIENT_CONTROL_MIC, { (it[0] as JSONObject).let { it.getBoolean("success") && it.getString("speaker") == logonUser.get().id } },
                JSONObject().put("roomId", conversationId))
                .doOnNext {
                    if (it) {
                        ensureCurrentSpeakerSubject(conversationId).onNext(logonUser.get()?.id)
                    }
                }
                .mergeWith(reifiedErrorSubject())
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        return socket.sendEvent(EVENT_CLIENT_RELEASE_MIC, {}, JSONObject().put("roomId", conversationId))
    }

    override fun resumeLogin(token: Serializable): Observable<LoginResult> {
        if (token is String) {
            val matcher = Pattern.compile("(.+?):(.+?)").matcher(token)
            if (matcher.matches()) {
                return login(matcher.group(1).decodeBase64(), matcher.group(2).decodeBase64())
            }
        }

        return Observable.error(InvalidSavedTokenException())
    }

    override fun login(username: String, password: String) = doLogin {
        it.put("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))
    }.map { LoginResult(it, "${username.toBase64()}:${password.toBase64()}") }

    private fun doLogin(headerOperator: (MutableMap<String, List<String>>) -> Unit): Observable<Person> {
        return IO.socket(endpoint).let {
            Observable.create<Person> { subscriber ->

                // 绑定IO事件,以便操作Headers
                it.io().on(Manager.EVENT_TRANSPORT, {
                    (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, { headerOperator(it[0] as MutableMap<String, List<String>>) })
                })

                // 监听用户登陆事件
                it.on(EVENT_SERVER_USER_LOGON, { args ->
                    try {
                        subscriber.onNext(parseServerResult(args, { it -> Person().readFrom(it[0] as JSONObject) }))
                    } catch(e: Exception) {
                        subscriber.onError(e)
                        subscriber.onCompleted()
                    }
                })

                // 监听出错事件
                val errorHandler: (Array<Any>) -> Unit = { errorSubject.onError(ServerException(it.getOrNull(0)?.toString() ?: "Unknown exception: " + it)) }
                it.on(Socket.EVENT_ERROR, errorHandler)
                it.on(Socket.EVENT_CONNECT_ERROR, errorHandler)
                it.on(Socket.EVENT_CONNECT_TIMEOUT, { errorSubject.onError(TimeoutException()) })

                // 监听房间在线成员变化事件
                it.on(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED, {
                    val event = parseServerResult(it, { it[0] as JSONObject })
                    ensureActiveMemberSubject(event.getString("roomId"))
                            .onNext(event.getJSONArray("activeMembers").toStringList())
                })

                // 监听得Mic人变化的事件
                it.on(EVENT_SERVER_SPEAKER_CHANGED, {
                    val event = parseServerResult(it, { it[0] as JSONObject })
                    ensureCurrentSpeakerSubject(event.getString("roomId"))
                            .onNext(event.let { if (it.isNull("speaker")) null else it.getString("speaker") })
                })

                // 监听房间成员变化的事件
                it.on(EVENT_SERVER_ROOM_INFO_CHANGED, {
                    val event = parseServerResult(it, { it[0] as JSONObject })
                    broker.updateConversationMembers(event.getString("idNumber"), event.getJSONArray("members").toStringList())
                })

                subscriber.add(Subscriptions.create {
                    it.close()
                })

                socket = it.connect()
            }.flatMap { person ->
                logonUser.set(person) // 设置当前用户
                syncContacts().map { person } // 在登陆完成以后等待通讯录同步
            }.mergeWith(reifiedErrorSubject())
        }
    }

    override fun logout(): Observable<Unit> {
        logonUser.set(null)
        socket.close()
        return Observable.empty()
    }

    fun syncContacts() = socket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, {
        val result: JSONObject = it[0] as JSONObject
        val personBuf = Person()
        var groupBuf = Group()
        val persons = result.getJSONObject("enterpriseMembers").getJSONArray("add").transform { personBuf.readFrom(it as JSONObject) }
        val groupJsonArray = result.getJSONObject("enterpriseGroups").getJSONArray("add")
        val groups = groupJsonArray.transform { groupBuf.readFrom(it as JSONObject) }
        Triple(persons, groupJsonArray, groups)
    }, JSONObject().put("enterMemberVersion", 1).put("enterGroupVersion", 1))
            .flatMap {
                Observable.concat(
                        broker.updateAllPersons(it.first),
                        broker.updateAllGroups(it.third, it.second.toGroupsAndMembers()),
                        broker.updateAllContacts(it.first.transform { it.id }, it.third.transform { it.id }))
            }
}

internal inline fun <T> parseServerResult(args: Array<Any?>, resultMapper: (Array<Any?>) -> T): T {
    if (args.isEmpty()) {
        throw ServerException("No response from server")
    }

    val arg = args[0]
    if (arg is JSONObject && arg.has("error")) {
        throw ServerException(arg.getString("error"))
    }

    return resultMapper(args)
}


internal fun CreateConversationRequest.toJSON(): JSONArray {
    // 0代表通讯组 1代表联系人
    return when (this) {
        is CreateConversationFromPerson -> JSONArray().put(JSONObject().put("srcType", 1).put("srcData", personId))
        is CreateConversationFromGroup -> JSONArray().put(JSONObject().put("srcType", 0).put("srcData", groupId))
        else -> throw IllegalArgumentException("Unknown request type: " + this)
    }
}

internal fun Group.readFrom(obj : JSONObject) : Group {
    id = obj.getString("idNumber")
    description = obj.optString("description")
    name = obj.getString("name")
    avatar = obj.optString("avatar")
    return this
}

internal fun Person.readFrom(obj: JSONObject): Person {
    id = obj.getString("idNumber")
    name = obj.getString("name")
    avatar = obj.optString("avatar")
    privileges = obj.optJSONObject("privileges").toPrivilege()
    return this
}

internal fun Conversation.readFrom(obj : JSONObject) : Conversation {
    id = obj.getString("idNumber")
    name = obj.getString("name")
    ownerId = obj.getString("owner")
    important = obj.getBoolean("important")
    return this
}

internal fun JSONObject.toRoomInfo(conversationId: String, logonUserId: String): RoomInfo {
    val server = getJSONObject("server")
    val roomInfoObject = getJSONObject("roomInfo")
    return RoomInfo(roomInfoObject.getString("idNumber"), conversationId,
            roomInfoObject.getJSONArray("members").toStringIterable().toList(),
            getJSONArray("activeMembers").toStringList(), optString("speaker"),
            mapOf(Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_IP, server.getString("host")),
                    Pair(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID, logonUserId),
                    Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT, server.getInt("port")),
                    Pair(WebRtcTalkEngine.PROPERTY_PROTOCOL, server.getString("protocol")))
    )

}

internal fun JSONArray?.toGroupsAndMembers(): Map<String, Iterable<String>> {
    if (this == null) {
        return emptyMap()
    }

    val size = length()
    val result = ArrayMap<String, Iterable<String>>(size)
    for (i in 1..size - 1) {
        val groupObject = getJSONObject(i)
        result.put(groupObject.getString("idNumber"), groupObject.optJSONArray("members").toStringIterable())
    }

    return result
}


internal fun JSONObject?.toPrivilege(): EnumSet<Privilege> {
    val result = EnumSet.noneOf(Privilege::class.java)

    this?.let {
        if (hasPrivilege("call")) {
            result += Privilege.MAKE_CALL
        }

        if (hasPrivilege("group")) {
            result += Privilege.CREATE_GROUP
        }

        if (hasPrivilege("recvCall")) {
            result += Privilege.RECEIVE_CALL
        }

        if (hasPrivilege("recvGroup")) {
            result += Privilege.RECEIVE_GROUP
        }
    }

    return result
}

private inline fun <T> Socket.sendEvent(eventName: String, crossinline resultMapper: (Array<Any?>) -> T, vararg args: Any?) = Observable.create<T> { subscriber ->
    subscriber.onStart()
    emit(eventName, *args, Ack {
        try {
            subscriber.onNext(parseServerResult(it, resultMapper))
        } catch(e: Throwable) {
            subscriber.onError(e)
        } finally {
            subscriber.onCompleted()
        }
    })
}


internal fun JSONObject.hasPrivilege(name: String) = has(name) && getBoolean(name)
