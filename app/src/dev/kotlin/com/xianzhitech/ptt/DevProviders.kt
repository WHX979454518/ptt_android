package com.xianzhitech.ptt

import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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


class DevAuthProvider(private val broker: Broker) : AuthProvider, SignalProvider {
    private var logonUserSubject = BehaviorSubject.create<Person>()
    private val conversations = hashMapOf<String, Pair<Conversation, MutableCollection<String>>>()
    private val conversationIdSeq = AtomicInteger(0)

    @Synchronized
    override fun login(username: String, password: String): Observable<LoginResult> {
        if (!logonUserSubject.hasValue()) {
            logonUserSubject.onNext(Person("my_id", username, EnumSet.allOf(Privilege::class.java)))
        }

        return logonUserSubject.map { LoginResult(it, username) }
                .doOnNext {
                    broker.updatePersons(DEV_UESRS)
                    broker.updateGroups(DEV_GROUPS, DEV_GROUP_MEMBERS)
                    broker.updateContacts(DEV_UESRS.transform { it.id }, DEV_GROUPS.transform { it.id })
                }
    }

    override fun resumeLogin(token: Serializable) = login(token as String, "")

    override fun getLogonPersonId() = logonUserSubject.value?.id

    override fun logout(): Observable<Unit> {
        logonUserSubject = BehaviorSubject.create()
        return Observable.just(null)
    }

    override fun createConversation(request: CreateConversationRequest): Observable<Conversation> {
        val logonUserId = getLogonPersonId() ?: throw IllegalStateException("Not logon")
        when (request) {
            is CreateConversationFromGroup -> {
                val group = DEV_GROUPS.first { it.id == request.groupId }
                val convMembers = DEV_GROUP_MEMBERS[group.id]?.toMutableSet() ?: hashSetOf()
                convMembers += logonUserId
                val conversation = Conversation(conversationIdSeq.incrementAndGet().toString(),
                        request.name ?: group.name,
                        null,
                        logonUserId,
                        false)

                conversations[conversation.id] = Pair(conversation, convMembers)
                return conversation.toObservable()
            }

            is CreateConversationFromPerson -> {
                val person = DEV_UESRS.first { it.id == request.personId }
                if (person.id == logonUserId) {
                    throw IllegalArgumentException("Can't create group for just one person")
                }

                val convMembers = hashSetOf(logonUserId, person.id)
                val conversation = Conversation(conversationIdSeq.incrementAndGet().toString(),
                        request.name ?: "",
                        null,
                        logonUserId,
                        false)
                conversations[conversation.id] = Pair(conversation, convMembers)
                return conversation.toObservable()
            }

            else -> {
                throw IllegalArgumentException("Unknown request: $request")
            }
        }

    }

    override fun deleteConversation(conversationId: String): Observable<Unit> {
        val logonUserId = getLogonPersonId() ?: throw IllegalStateException("Not logon")
        val conv = conversations.get(conversationId)
        if (conv != null && conv.first.ownerId == logonUserId) {
            conversations.remove(conversationId)
        }

        return Observable.empty()
    }

    override fun joinConversation(conversationId: String): Observable<Room> {
        throw UnsupportedOperationException()
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        throw UnsupportedOperationException()
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        throw UnsupportedOperationException()
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        throw UnsupportedOperationException()
    }
}