package com.xianzhitech.ptt

import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.CreateConversationRequest
import com.xianzhitech.ptt.service.provider.LoginResult
import com.xianzhitech.ptt.service.provider.SignalProvider
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.emptyMap
import kotlin.collections.hashSetOf
import kotlin.collections.listOf
import kotlin.collections.mapOf

/**
 * Created by fanchao on 27/12/15.
 */

internal val DEV_UESRS = listOf<Person>(
        Person("1", "用户1", EnumSet.allOf(Privilege::class.java)),
        Person("2", "用户2", EnumSet.allOf(Privilege::class.java)),
        Person("3", "用户3", EnumSet.allOf(Privilege::class.java)),
        Person("4", "用户4", EnumSet.allOf(Privilege::class.java)),
        Person("5", "用户5", EnumSet.allOf(Privilege::class.java)),
        Person("6", "用户6", EnumSet.allOf(Privilege::class.java)),
        Person("7", "回声用户7", EnumSet.allOf(Privilege::class.java))
)

internal val DEV_GROUPS = listOf<Group>(
        Group("1", "组1"),
        Group("2", "组2"),
        Group("3", "组3"),
        Group("4", "组4"),
        Group("5", "组5"),
        Group("6", "组6")
)

internal val DEV_GROUP_MEMBERS = mapOf(
        Pair(DEV_GROUPS[0].id, listOf(DEV_UESRS[0].id, DEV_UESRS[1].id)),
        Pair(DEV_GROUPS[1].id, listOf(DEV_UESRS[1].id, DEV_UESRS[2].id)),
        Pair(DEV_GROUPS[2].id, listOf(DEV_UESRS[2].id, DEV_UESRS[3].id)),
        Pair(DEV_GROUPS[3].id, listOf(DEV_UESRS[3].id, DEV_UESRS[4].id)),
        Pair(DEV_GROUPS[4].id, listOf(DEV_UESRS[4].id, DEV_UESRS[5].id)),
        Pair(DEV_GROUPS[5].id, listOf(DEV_UESRS[5].id, DEV_UESRS[6].id))
)

internal val DEV_CONVERSATIONS = listOf(
        Conversation("1", "会话1", null, DEV_UESRS[0].id, false),
        Conversation("2", "会话2", null, DEV_UESRS[0].id, false),
        Conversation("3", "会话3", null, DEV_UESRS[0].id, false),
        Conversation("4", "会话4", null, DEV_UESRS[0].id, false),
        Conversation("5", "会话5", null, DEV_UESRS[0].id, false)
)

internal data class ActiveRoomInfo(val conversation: Conversation) {
    val members: MutableCollection<String> = hashSetOf()
    val currSpeaker = AtomicReference<String>()
}


class DevProvider(private val broker: Broker) : AuthProvider, SignalProvider {
    private val room = ActiveRoomInfo(Conversation("1", "回声会话", null, DEV_UESRS[0].id, false))
    private val logonUser = AtomicReference<Person>()
    private val roomInfoSubject = BehaviorSubject.create<RoomInfo>()

    init {
        room.members.add(DEV_UESRS[6].id)
    }

    @Synchronized
    override fun login(username: String, password: String): Observable<LoginResult> {
        logonUser.set(Person("my_id", username, EnumSet.allOf(Privilege::class.java)))

        broker.updatePersons(DEV_UESRS).subscribe()
        broker.updateGroups(DEV_GROUPS, DEV_GROUP_MEMBERS).subscribe()
        broker.updateContacts(DEV_UESRS.transform { it.id }, DEV_GROUPS.transform { it.id }).subscribe()

        return LoginResult(logonUser.get(), null).toObservable()
    }

    override fun resumeLogin(token: Serializable) = login(token as String, "")

    override val currentLogonUserId: String?
        get() = logonUser.get()?.id

    private val ensuredLogonUserId: String
        get() = currentLogonUserId ?: throw IllegalStateException("Not logon")

    override fun logout(): Observable<Unit> {
        logonUser.set(null)
        return Observable.just(null)
    }

    override fun createConversation(request: CreateConversationRequest) = room.conversation.toObservable()
    override fun deleteConversation(conversationId: String) = Observable.empty<Unit>()

    override fun joinConversation(conversationId: String) = room.members.add(ensuredLogonUserId).let {
        roomInfoSubject.onNext(RoomInfo(conversationId, conversationId, room.members, room.currSpeaker.get(), emptyMap()))
        roomInfoSubject
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        room.members.remove(ensuredLogonUserId)
        return Observable.empty()
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return Observable.timer(1, TimeUnit.SECONDS).map {
            room.currSpeaker.compareAndSet(null, ensuredLogonUserId)
        }
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        room.currSpeaker.compareAndSet(ensuredLogonUserId, null)
        return Observable.empty()
    }
}

class DevTalkEngine : TalkEngine {
    override fun connect(roomInfo: RoomInfo) {
    }

    override fun dispose() {
    }

    override fun startSend() {
    }

    override fun stopSend() {
    }
}