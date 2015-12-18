package com.xianzhitech.ptt

import com.google.common.collect.ImmutableSet
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.provider.CreateConversationRequest
import com.xianzhitech.ptt.service.provider.CreateGroupConversationRequest
import com.xianzhitech.ptt.service.provider.CreatePersonConversationRequest
import com.xianzhitech.ptt.service.provider.SignalProvider
import rx.Observable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by fanchao on 18/12/15.
 */

class MockSignalProvider(val currentUser: Person,
                         val persons: List<Person>,
                         val groups: List<Group>,
                         val groupMembers: Map<String, Iterable<String>>) : SignalProvider {

    private val idSeq = AtomicInteger(0)

    val conversations = HashMap<Conversation, RoomInfo>()

    override fun createConversation(requests: Iterable<CreateConversationRequest>): Observable<Conversation> {
        val request = requests.iterator().next()

        when (request) {
            is CreatePersonConversationRequest -> {
                // 寻找已有的会话
                val setToFind = hashSetOf(currentUser.id, request.personId)
                var foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.key.toObservable()
                }

                // 新建一个会话
                val conv = Conversation(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv, RoomInfo(arrayListOf(), null))
            }

            is CreateGroupConversationRequest -> {
                val members = groupMembers.get(request.groupId) ?: return Observable.error(IllegalArgumentException("Group ${request.groupId} does not exist"))

                val setToFind = ImmutableSet.copyOf(members)
                conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
            }
        }
    }

    override fun deleteConversation(conversationId: String): Observable<Void> {
        throw UnsupportedOperationException()
    }

    override fun joinConversation(conversationId: String): Observable<Room> {

        throw UnsupportedOperationException()
    }

    override fun quitConversation(conversationId: String): Observable<Void> {
        throw UnsupportedOperationException()
    }

    override fun requestFocus(roomId: Int): Observable<Boolean> {
        throw UnsupportedOperationException()
    }

    override fun releaseFocus(roomId: Int): Observable<Void> {
        throw UnsupportedOperationException()
    }

}

class RoomInfo(val members: MutableList<Person>, var speaker: Person?)