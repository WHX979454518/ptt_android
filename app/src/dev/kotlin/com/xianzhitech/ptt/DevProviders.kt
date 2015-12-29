package com.xianzhitech.ptt

import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.*

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
        Person("7", "用户7", EnumSet.allOf(Privilege::class.java))
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

internal data class RoomInfo(val conversation: Conversation) {
    val members: MutableCollection<String> = hashSetOf()
    val currSpeaker = AtomicReference<String>()
}


class DevProvider(private val broker: Broker) : AuthProvider, SignalProvider {
    private val rooms = hashMapOf<String, RoomInfo>()
    private val conversationIdSeq = AtomicInteger(0)
    private val logonUser = AtomicReference<Person>()

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

    private fun ensureRoom(conversationId: String) = rooms[conversationId] ?: throw IllegalStateException("No room info for $conversationId")

    override fun logout(): Observable<Unit> {
        logonUser.set(null)
        return Observable.just(null)
    }

    override fun createConversation(request: CreateConversationRequest): Observable<Conversation> {
        when (request) {
            is CreateConversationFromGroup -> {
                val group = DEV_GROUPS.first { it.id == request.groupId }
                val convMembers = DEV_GROUP_MEMBERS[group.id]?.toMutableSet() ?: hashSetOf()
                convMembers += ensuredLogonUserId
                val conversation = Conversation(conversationIdSeq.incrementAndGet().toString(),
                        request.name ?: group.name,
                        null,
                        ensuredLogonUserId,
                        false)

                rooms[conversation.id] = RoomInfo(conversation)
                return conversation.toObservable()
            }

            is CreateConversationFromPerson -> {
                val person = DEV_UESRS.first { it.id == request.personId }
                if (person.id == ensuredLogonUserId) {
                    throw IllegalArgumentException("Can't create group for just one person")
                }

                val convMembers = hashSetOf(ensuredLogonUserId, person.id)
                val conversation = Conversation(conversationIdSeq.incrementAndGet().toString(),
                        request.name ?: "",
                        null,
                        ensuredLogonUserId,
                        false)
                rooms[conversation.id] = RoomInfo(conversation)
                return conversation.toObservable()
            }

            else -> {
                throw IllegalArgumentException("Unknown request: $request")
            }
        }

    }

    override fun deleteConversation(conversationId: String): Observable<Unit> {
        ensureRoom(conversationId).apply {
            if (conversation.ownerId == ensuredLogonUserId) {
                rooms.remove(conversationId)
            }
        }

        return Observable.empty()
    }

    override fun joinConversation(conversationId: String) = ensureRoom(conversationId).let {
        it.members += currentLogonUserId ?: throw IllegalStateException()
        Room(conversationId, conversationId, it.members, it.currSpeaker.get(), emptyMap()).toObservable()
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        ensureRoom(conversationId).let {
            it.members.remove(ensuredLogonUserId)
        }

        return Observable.empty()
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return Observable.timer(1, TimeUnit.SECONDS).map {
            ensureRoom(conversationId).let {
                it.currSpeaker.compareAndSet(null, ensuredLogonUserId)
            }
        }
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        ensureRoom(conversationId).let {
            it.currSpeaker.compareAndSet(ensuredLogonUserId, null)
        }

        return Observable.empty()
    }
}

class DevTalkEngine : TalkEngine {
    override fun connect(room: Room) {
    }

    override fun dispose() {
    }

    override fun startSend() {
    }

    override fun stopSend() {
    }
}