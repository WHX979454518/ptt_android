package com.xianzhitech.ptt.service

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by fanchao on 18/12/15.
 */

object MockPersons {
    val PERSON_1 = Person("1", "person 1", Privilege.CREATE_GROUP or Privilege.MAKE_CALL)
    val PERSON_2 = Person("2", "person 2", Privilege.CREATE_GROUP or Privilege.MAKE_CALL)
    val PERSON_3 = Person("3", "person 3", Privilege.CREATE_GROUP or Privilege.MAKE_CALL)
    val PERSON_4 = Person("4", "person 4", Privilege.CREATE_GROUP or Privilege.MAKE_CALL)
    val PERSON_5 = Person("5", "person 5", Privilege.CREATE_GROUP or Privilege.MAKE_CALL)

    val ALL = listOf(PERSON_1, PERSON_2, PERSON_3, PERSON_4, PERSON_5)
}

object MockGroups {
    val GROUP_1 = Group("1", "Group 1")
    val GROUP_2 = Group("2", "Group 1")
    val GROUP_3 = Group("3", "Group 1")

    val GROUP_MEMBERS = ImmutableMap.of(
            GROUP_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_2.id),
            GROUP_2.id, listOf(MockPersons.PERSON_3.id, MockPersons.PERSON_4.id),
            GROUP_3.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_4.id, MockPersons.PERSON_5.id))

    val ALL = listOf(GROUP_1, GROUP_2, GROUP_3)
}

class MockSignalProvider(val currentUser: Person,
                         val persons: List<Person>,
                         val groups: List<Group>,
                         val groupMembers: Map<String, Iterable<String>>) : SignalProvider {

    private val idSeq = AtomicInteger(0)

    val conversations = HashMap<String, RoomInfo>()

    override fun createConversation(requests: Iterable<CreateConversationRequest>): Observable<Conversation> {
        val request = requests.iterator().next()

        when (request) {
            is CreatePersonConversationRequest -> {
                // 寻找已有的会话
                val setToFind = hashSetOf(currentUser.id, request.personId)
                var foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                // 新建一个会话
                val conv = Conversation(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, RoomInfo(conv, setToFind))
                return conv.toObservable()
            }

            is CreateGroupConversationRequest -> {
                val members = groupMembers.get(request.groupId) ?: return Observable.error(IllegalArgumentException("Group ${request.groupId} does not exist"))

                val setToFind = ImmutableSet.copyOf(members)
                val foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                val conv = Conversation(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, RoomInfo(conv, members.toHashSet()))
                return conv.toObservable()
            }

            else -> throw IllegalArgumentException("Unknown request: $request")
        }
    }

    override fun deleteConversation(conversationId: String): Observable<Void> {
        val roomInfo = conversations[conversationId] ?: return Observable.just(null)

        if (roomInfo.conversation.ownerId != currentUser.id) {
            return Observable.error(IllegalAccessError())
        }

        conversations.remove(conversationId)
        return Observable.just(null)
    }

    override fun joinConversation(conversationId: String): Observable<Room> {
        val roomInfo = conversations[conversationId] ?: return Observable.error(IllegalArgumentException())
        roomInfo.activeMembers += currentUser.id
        return Room(roomInfo.conversation.id, roomInfo.activeMembers.toList(), roomInfo.speaker.get(), "host", 80, "tcp").toObservable()
    }

    override fun quitConversation(conversationId: String): Observable<Void> {
        val roomInfo = conversations[conversationId] ?: return Observable.error(IllegalArgumentException())
        roomInfo.activeMembers -= currentUser.id
        return Observable.just(null)
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        val roomInfo = conversations[conversationId] ?: return Observable.error(IllegalArgumentException())
        return roomInfo.speaker.compareAndSet(null, currentUser.id).toObservable()
    }

    override fun releaseMic(conversationId: String): Observable<Void> {
        val roomInfo = conversations[conversationId] ?: return Observable.error(IllegalArgumentException())
        roomInfo.speaker.compareAndSet(currentUser.id, null)
        return Observable.just(null)
    }

    class RoomInfo(val conversation : Conversation,
                   val members: MutableSet<String> = hashSetOf(),
                   val activeMembers: MutableSet<String> = hashSetOf(),
                   var speaker: AtomicReference<String> = AtomicReference<String>(null))


}


class MockTalkEngine : TalkEngine {
    var connectedRoom : Room? = null
    var disposed = false
    var sending = false;

    override fun connect(room: Room) {
        if (disposed) {
            throw IllegalStateException("Already disposed")
        }
        connectedRoom = room
    }

    override fun dispose() {
        disposed = true;
        connectedRoom = null
    }

    override fun startSend() {
        if (disposed) {
            throw IllegalStateException("Already disposed")
        }

        sending = true
    }

    override fun stopSend() {
        if (disposed) {
            throw IllegalStateException("Already disposed")
        }

        sending = false
    }

}

class MockTalkEngineProvider : TalkEngineProvider {
    override fun createEngine() = MockTalkEngine()
}

class MockAuthProvider : AuthProvider {
    var logonUser: Person? = null

    override fun login(username: String, password: String) =
            (MockPersons.ALL.firstOrNull { it.name == username }?.toObservable() ?: Observable.error(IllegalArgumentException()))
                    .doOnNext { logonUser = it }

    override fun logout(): Observable<Void> {
        logonUser = null
        return Observable.just(null)
    }

}