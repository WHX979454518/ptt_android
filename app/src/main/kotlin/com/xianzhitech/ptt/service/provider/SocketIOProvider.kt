package com.xianzhitech.ptt.service.provider

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.signal.Room
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import rx.subjects.ReplaySubject
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 * 用Socket.io写的一个服务器
 *
 * Created by fanchao on 17/12/15.
 */
class SocketIOProvider(private val broker: Broker, private val endpoint: String) : SignalProvider, AuthProvider {
    companion object {
        public const val EVENT_SERVER_USER_LOGON = "s_logon"

        public const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        public const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
    }

    private val hasInitializedSocket = AtomicBoolean(false)
    private val socketSubject = BehaviorSubject.create<Socket>()
    private val logonUserSubject = ReplaySubject.createWithSize<Person>(1)
    private val eventSubject = PublishSubject.create<Event>()

    override fun createConversation(requests: Iterable<CreateConversationRequest>): Observable<Conversation> {
        return socketSubject.flatMap({it.sendEvent(
                EVENT_CLIENT_CREATE_ROOM,
                { Conversation().readFrom(it as JSONObject) },
                arrayOf(requests.toJSONArray { it.toJSON() })) ?: Observable.error(IllegalStateException("Not logon"))
        })
    }

    override fun deleteConversation(conversationId: String) : Observable<Void> {
        return Observable.empty()
    }

    override fun joinConversation(conversationId: String): Observable<Room> {
        return Observable.empty()
    }

    override fun quitConversation(conversationId: String) : Observable<Void> {
        return Observable.empty()
    }

    override fun requestFocus(roomId: Int): Observable<Boolean> {
        return Observable.empty()
    }

    override fun releaseFocus(roomId: Int) : Observable<Void> {
        return Observable.empty()
    }

    override fun login(username: String, password: String): Observable<Person> {
        if (!hasInitializedSocket.compareAndSet(false, true)) {
            return logonUserSubject;
        }

        val socket = IO.socket(endpoint).let {
            it.io().on(Manager.EVENT_TRANSPORT, { args: Array<Any> ->
                val arg = args[0] as Transport
                arg.on(Transport.EVENT_REQUEST_HEADERS, {
                    (it[0] as MutableMap<String, List<String>>).put("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))
                })
            })

            it.subscribeTo(
                    Socket.EVENT_CONNECT,
                    Socket.EVENT_CONNECT_ERROR,
                    Socket.EVENT_CONNECT_TIMEOUT,
                    Socket.EVENT_ERROR,
                    EVENT_SERVER_USER_LOGON)
            it
        }

        socketSubject.onNext(socket)

        return eventSubject
                .flatMap({ event: Event ->
                    when (event.name) {
                        Socket.EVENT_ERROR, Socket.EVENT_CONNECT_ERROR -> Observable.error<Person>(RuntimeException("Connection error: " + event.args))
                        Socket.EVENT_CONNECT_TIMEOUT -> Observable.error<Person>(TimeoutException())
                        Socket.EVENT_CONNECT -> {
                            Observable.empty<Person>()
                        }
                        EVENT_SERVER_USER_LOGON -> {
                            logonUserSubject.onNext(Person().readFrom(event.jsonObject))
                            syncContacts().subscribe()
                            logonUserSubject
                        }
                        else -> Observable.empty<Person>()
                    }
                })
                .doOnSubscribe { socket?.connect() }
    }

    override fun logout() : Observable<Void> {
        return Observable.empty()
    }

    fun syncContacts() = socketSubject.flatMap({
        it.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, {
            val result : JSONObject = it as JSONObject
            val personBuf = Person()
            var groupBuf = Group()
            val persons = result.getJSONObject("enterpriseMembers").getJSONArray("add").transform { personBuf.readFrom(it as JSONObject) }
            val groupJsonArray = result.getJSONObject("enterpriseGroups").getJSONArray("add")
            val groups = groupJsonArray.transform { groupBuf.readFrom(it as JSONObject) }
            Observable.concat(
                    broker.updatePersons(persons),
                    broker.updateGroups(groups, groupJsonArray.toGroupsAndMembers()),
                    broker.updateContacts(Iterables.transform(persons, { it.id }), Iterables.transform(groups, { it.id })))
                    .subscribe()
        }, ImmutableMap.of("enterMemberVersion", 1, "enterGroupVersion", 1).toJSONObject())
    })


    fun Socket.subscribeTo(vararg events: String): Socket {
        for (event in events) {
            on(event, { this@SocketIOProvider.eventSubject.onNext(Event(event, it)) })
        }
        return this
    }

}

internal fun CreateConversationRequest.toJSON() : JSONObject {
    val result = JSONObject()
    // 0代表通讯组 1代表联系人
    result.put("srcType", if (personId.isNullOrEmpty()) 0 else 1)
    result.put("srcData", if (personId.isNullOrEmpty()) groupId else personId)
    return result
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
    privilege = obj.optJSONObject("privileges").toPrivilege()
    return this
}

internal fun Conversation.readFrom(obj : JSONObject) : Conversation {
    id = obj.getString("idNumber")
    name = obj.getString("name")
    ownerId = obj.getString("owner")
    important = obj.getBoolean("important")
    return this
}

@Privilege
internal fun JSONObject?.toPrivilege(): Int {
    @Privilege var result = 0

    if (this == null) {
        return result
    }

    if (hasPrivilege("call")) {
        result = result or Privilege.MAKE_CALL
    }

    if (hasPrivilege("group")) {
        result = result or Privilege.CREATE_GROUP
    }

    if (hasPrivilege("recvCall")) {
        result = result or Privilege.RECEIVE_CALL
    }

    if (hasPrivilege("recvGroup")) {
        result = result or Privilege.RECEIVE_GROUP
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
