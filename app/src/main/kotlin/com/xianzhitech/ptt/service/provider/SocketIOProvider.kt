package com.xianzhitech.ptt.service.provider

import android.support.v4.util.ArrayMap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
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
    private val eventSubject = PublishSubject.create<Event>()

    override fun createConversation(request: CreateConversationRequest): Observable<Conversation> {
        return socket
                .sendEvent(EVENT_CLIENT_CREATE_ROOM, { it as JSONObject }, request.toJSON())
                .flatMap {
                    val conversation = Conversation().readFrom(it)
                    val members = it.getJSONArray("members").toStringIterable()

                    broker.saveConversation(conversation)
                    broker.updateConversationMembers(conversation.id, members).map { conversation }
                }
    }

    override fun deleteConversation(conversationId: String): Observable<Unit> {
        return Observable.empty()
    }

    override fun peekCurrentLogonUserId() = logonUser.get()?.id

    override fun joinConversation(conversationId: String): Observable<RoomInfo> {
        return socket.sendEvent(
                EVENT_CLIENT_JOIN_ROOM,
                { (it as JSONObject).toRoomInfo(conversationId) },
                JSONObject().put("roomId", conversationId))
                .doOnNext {
                    broker.updateConversationMembers(conversationId, it.members)
                    ensureActiveMemberSubject(conversationId).onNext(it.activeMembers)
                    ensureCurrentSpeakerSubject(conversationId).onNext(it.speaker)
                }
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        return socket.sendEvent(EVENT_CLIENT_LEAVE_ROOM, {}, JSONObject().put("roomId", conversationId))
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return socket.sendEvent(EVENT_CLIENT_CONTROL_MIC, { (it as JSONObject).let { it.getBoolean("success") && it.getString("speaker") == logonUser.get().id } },
                JSONObject().put("roomId", conversationId))
                .doOnNext {
                    if (it) {
                        ensureCurrentSpeakerSubject(conversationId).onNext(logonUser.get()?.id)
                    }
                }
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
        socket = IO.socket(endpoint).let {
            it.io().on(Manager.EVENT_TRANSPORT, { args: Array<Any> ->
                val arg = args[0] as Transport
                arg.on(Transport.EVENT_REQUEST_HEADERS, {
                    headerOperator(it[0] as MutableMap<String, List<String>>)
                })
            })

            it.subscribeTo(
                    Socket.EVENT_CONNECT,
                    Socket.EVENT_CONNECT_ERROR,
                    Socket.EVENT_CONNECT_TIMEOUT,
                    Socket.EVENT_ERROR,
                    EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED,
                    EVENT_SERVER_SPEAKER_CHANGED,
                    EVENT_SERVER_ROOM_INFO_CHANGED,
                    EVENT_SERVER_USER_LOGON)
            it
        }

        return eventSubject
                .flatMap({ event: Event ->
                    when (event.name) {
                        Socket.EVENT_ERROR, Socket.EVENT_CONNECT_ERROR -> Observable.error<Person>(RuntimeException("Connection error: " + event.args.firstOrNull()))
                        Socket.EVENT_CONNECT_TIMEOUT -> Observable.error<Person>(TimeoutException())
                        Socket.EVENT_CONNECT -> {
                            Observable.empty<Person>()
                        }

                        EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED -> {
                            val conversationId = event.jsonObject.getString("roomId")
                            val members = event.jsonObject.getJSONArray("activeMembers").toStringList()
                            ensureActiveMemberSubject(conversationId).onNext(members)
                            Observable.empty<Person>()
                        }

                        EVENT_SERVER_SPEAKER_CHANGED -> {
                            ensureCurrentSpeakerSubject(event.jsonObject.getString("roomId"))
                                    .onNext(event.jsonObject.let { if (it.isNull("speaker")) null else it.getString("speaker") })
                            Observable.empty<Person>()
                        }

                        EVENT_SERVER_ROOM_INFO_CHANGED -> {
                            broker.updateConversationMembers(event.jsonObject.getString("idNumber"), event.jsonObject.getJSONArray("members").toStringList())
                            Observable.empty<Person>()
                        }

                        EVENT_SERVER_USER_LOGON -> {
                            logonUser.set(Person().readFrom(event.jsonObject))
                            syncContacts().subscribe()
                            logonUser.get().toObservable()
                        }


                        else -> Observable.empty<Person>()
                    }
                })
                .doOnSubscribe { socket.connect() }
    }

    override fun logout(): Observable<Unit> {
        logonUser.set(null)
        return Observable.empty()
    }

    fun syncContacts() = socket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, {
        val result: JSONObject = it as JSONObject
        val personBuf = Person()
        var groupBuf = Group()
        val persons = result.getJSONObject("enterpriseMembers").getJSONArray("add").transform { personBuf.readFrom(it as JSONObject) }
        val groupJsonArray = result.getJSONObject("enterpriseGroups").getJSONArray("add")
        val groups = groupJsonArray.transform { groupBuf.readFrom(it as JSONObject) }
        Observable.concat(
                broker.updateAllPersons(persons),
                broker.updateAllGroups(groups, groupJsonArray.toGroupsAndMembers()),
                broker.updateAllContacts(Iterables.transform(persons, { it.id }), Iterables.transform(groups, { it.id })))
                .subscribe()
    }, ImmutableMap.of("enterMemberVersion", 1, "enterGroupVersion", 1).toJSONObject())


    fun Socket.subscribeTo(vararg events: String): Socket {
        for (event in events) {
            on(event, { this@SocketIOProvider.eventSubject.onNext(Event(event, it)) })
        }
        return this
    }

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

internal fun JSONObject.toRoomInfo(conversationId: String): RoomInfo {
    val server = getJSONObject("server")
    val roomInfoObject = getJSONObject("roomInfo")
    return RoomInfo(roomInfoObject.getString("idNumber"), conversationId,
            roomInfoObject.getJSONArray("members").toStringIterable().toList(),
            getJSONArray("activeMembers").toStringList(), optString("speaker"),
            ImmutableMap.of(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_IP, server.getString("host"),
                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT, server.getInt("port"),
                    WebRtcTalkEngine.PROPERTY_PROTOCOL, server.getString("protocol")))
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

private fun <T> Socket.sendEvent(eventName : String, resultMapper : (Any?) -> T?, vararg args : Any?) = Observable.create<T> {
    it.onStart()
    emit(eventName, *args, Ack { results : Array<Any?> ->
        val result = results[0]
        if (result is JSONObject && result.has("error")) {
            it.onError(ServerException(result.getString("error")))
            it.onCompleted()
        }
        else {
            it.onNext(resultMapper(result))
            it.onCompleted()
        }
    })
}

internal fun JSONObject.hasPrivilege(name: String) = has(name) && getBoolean(name)

internal data class Event(val name: String, val args: Array<Any>) {
    val jsonObject: JSONObject
        get() = args[0] as JSONObject

    val jsonArray: JSONArray
        get() = args[0] as JSONArray
}
