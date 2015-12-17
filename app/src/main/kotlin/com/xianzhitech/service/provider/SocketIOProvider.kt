package com.xianzhitech.service.provider

import com.google.common.collect.ImmutableMap
import com.xianzhitech.ext.*
import com.xianzhitech.model.Group
import com.xianzhitech.model.Person
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.service.signal.Room
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import rx.functions.Func1
import rx.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Created by fanchao on 17/12/15.
 */
class SocketIOProvider(private val broker: Broker, private val endpoint: String) : SignalProvider, AuthProvider {
    companion object {
        public const val EVENT_USER_LOGON = "userLogon"
        public const val EVENT_CONTACTS_UPDATE = "contactsUpdate"
    }

    private var socket: Socket? = null
    private val eventSubject = PublishSubject.create<Event>()

    init {
        eventSubject.filter({ it.name == EVENT_CONTACTS_UPDATE })
                .flatMap(Func1<Event, Observable<Void>> {
                    val personBuf = Person()
                    var groupBuf = Group()
                    val persons = it.jsonObject.getJSONObject("enterpriseMembers").getJSONArray("add").transform { personBuf.readFrom(it as JSONObject) }
                    val groups = it.jsonObject.getJSONObject("enterpriseGroups").getJSONArray("add").transform { groupBuf. }
                    Observable.concat(
                            broker.updatePersons(persons),
                            broker.updateGroups()
                    )
                })
                .subscribe()
    }

    override fun joinRoom(groupId: String): Room {
        throw UnsupportedOperationException()
    }

    override fun quitRoom(groupId: String) {
        throw UnsupportedOperationException()
    }

    override fun requestFocus(roomId: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun releaseFocus(roomId: Int) {
        throw UnsupportedOperationException()
    }

    override fun login(username: String, password: String): Person {
        if (socket != null) {
            throw IllegalStateException("Already logon")
        }

        socket = IO.socket(endpoint).let {
            it.on(Manager.EVENT_TRANSPORT, { args: Array<Any> ->
                val arg = args[0] as Transport
                arg.on(Transport.EVENT_REQUEST_HEADERS, {
                    (it[0] as MutableMap<String, List<String>>).put("Authorization", listOf("Basic ${password.toMD5()}".toBase64()))
                })
            })

            it.subscribeTo(Socket.EVENT_CONNECT, Socket.EVENT_CONNECT_ERROR,
                    Socket.EVENT_CONNECT_TIMEOUT, Socket.EVENT_ERROR,
                    EVENT_CONTACTS_UPDATE, EVENT_USER_LOGON)
            it
        }

        return eventSubject
                .flatMap({ event: Event ->
                    when (event.name) {
                        Socket.EVENT_ERROR, Socket.EVENT_CONNECT_ERROR -> Observable.error<Person>(RuntimeException("Connection error: " + event.args))
                        Socket.EVENT_CONNECT_TIMEOUT -> Observable.error<Person>(TimeoutException())
                        Socket.EVENT_CONNECT -> {
                            socket?.emit("syncContacts", ImmutableMap.of("enterMemberVersion", 1, "enterGroupVersion", 1).toJSONObject())
                            Observable.empty<Person>()
                        }
                        EVENT_USER_LOGON -> Observable.just<Person>(Person().readFrom(event.jsonObject))
                        else -> Observable.empty<Person>()
                    }
                })
                .doOnSubscribe { socket?.connect() }
                .toBlockingFirst()
    }

    override fun logout() {
        throw UnsupportedOperationException()
    }

    fun Socket.subscribeTo(vararg events: String): Socket {
        for (event in events) {
            on(event, { this@SocketIOProvider.eventSubject.onNext(Event(event, it)) })
        }
        return this
    }

}

internal data class Event(val name: String, val args: Array<Any>) {
    val jsonObject: JSONObject
        get() = args[0] as JSONObject

    val jsonArray: JSONArray
        get() = args[0] as JSONArray
}
