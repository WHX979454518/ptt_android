package com.xianzhitech.ptt.service

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import java.io.Serializable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by fanchao on 18/12/15.
 */

object MockPersons {
    val PERSON_1 = Person("1", "person 1", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_2 = Person("2", "person 2", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_3 = Person("3", "person 3", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_4 = Person("4", "person 4", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_5 = Person("5", "person 5", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))

    val ALL = listOf(PERSON_1, PERSON_2, PERSON_3, PERSON_4, PERSON_5)
}

object MockGroups {
    val GROUP_1 = Group("1", "Group 1")
    val GROUP_2 = Group("2", "Group 2")
    val GROUP_3 = Group("3", "Group 3")
    val GROUP_4 = Group("4", "Group 4")

    val GROUP_MEMBERS = ImmutableMap.of(
            GROUP_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_2.id),
            GROUP_2.id, listOf(MockPersons.PERSON_3.id, MockPersons.PERSON_4.id),
            GROUP_3.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_4.id, MockPersons.PERSON_5.id))

    val ALL = listOf(GROUP_1, GROUP_2, GROUP_3)
}

object MockConversations {
    val CONVERSATION_1 = Conversation("1", "Conversation 1", null, MockPersons.PERSON_1.id, false)
    val CONVERSATION_2 = Conversation("2", "Conversation 2", null, MockPersons.PERSON_2.id, false)

    val CONVERSATION_MEMBERS = ImmutableMap.of<String, Iterable<String>>(
            CONVERSATION_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_3.id),
            CONVERSATION_2.id, listOf(MockPersons.PERSON_2.id, MockPersons.PERSON_4.id))

    val ALL = listOf(CONVERSATION_1, CONVERSATION_2)
}

class MockSignalProvider(val currentUser: Person,
                         val persons: List<Person>,
                         val groups: List<Group>,
                         val groupMembers: Map<String, Iterable<String>>) : AbstractSignalProvider() {

    private val idSeq = AtomicInteger(0)

    val conversations = HashMap<String, ConversationInfo>()

    override fun createConversation(request: CreateConversationRequest): Observable<Conversation> {
        when (request) {
            is CreateConversationFromPerson -> {
                // 寻找已有的会话
                val setToFind = hashSetOf(currentUser.id, request.personId)
                var foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                // 新建一个会话
                val conv = Conversation(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, ConversationInfo(conv, setToFind))
                return conv.toObservable()
            }

            is CreateConversationFromGroup -> {
                val members = groupMembers.get(request.groupId) ?: return Observable.error(IllegalArgumentException("Group ${request.groupId} does not exist"))

                val setToFind = ImmutableSet.copyOf(members)
                val foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                val conv = Conversation(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, ConversationInfo(conv, members.toHashSet()))
                return conv.toObservable()
            }

            else -> throw IllegalArgumentException("Unknown request: $request")
        }
    }

    override fun deleteConversation(conversationId: String): Observable<Unit> {
        val roomInfo = conversations[conversationId] ?: return Observable.just(null)

        if (roomInfo.conversation.ownerId != currentUser.id) {
            return Observable.error(IllegalAccessError())
        }

        conversations.remove(conversationId)
        return Observable.just(null)
    }

    override fun joinConversation(conversationId: String): Observable<RoomInfo> {
        val roomInfo = conversations[conversationId] ?: return Observable.error(IllegalArgumentException())
        ensureActiveMemberSubject(conversationId).onNext(peekActiveMemberIds(conversationId) + currentUser.id)
        return RoomInfo(roomInfo.conversation.id, roomInfo.conversation.id, roomInfo.members, peekActiveMemberIds(conversationId),
                peekCurrentSpeakerId(conversationId), emptyMap()).toObservable()
    }

    override fun quitConversation(conversationId: String): Observable<Unit> {
        ensureActiveMemberSubject(conversationId).onNext(peekActiveMemberIds(conversationId) - currentUser.id)
        return Observable.just(null)
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return (if (peekCurrentSpeakerId(conversationId) == null) {
            ensureCurrentSpeakerSubject(conversationId).onNext(currentUser.id)
            true
        } else false).toObservable()
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        if (peekCurrentSpeakerId(conversationId) == currentUser.id) {
            ensureCurrentSpeakerSubject(conversationId).onNext(null)
        }
        return Observable.just(null)
    }

    class ConversationInfo(val conversation: Conversation,
                           val members: MutableSet<String> = hashSetOf())


}


class MockTalkEngine : TalkEngine {
    var connectedRoomInfo: RoomInfo? = null
    var disposed = false
    var sending = false;

    override fun connect(roomInfo: RoomInfo) {
        if (disposed) {
            throw IllegalStateException("Already disposed")
        }
        connectedRoomInfo = roomInfo
    }

    override fun dispose() {
        disposed = true;
        connectedRoomInfo = null
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
    val createdEngines: MutableList<MockTalkEngine> = arrayListOf()

    override fun createEngine() = MockTalkEngine().let { createdEngines += it; it }
}

class MockAuthProvider : AuthProvider {
    var logonUser: Person? = null

    override fun login(username: String, password: String) =
            (MockPersons.ALL.firstOrNull { it.name == username }?.toObservable()?.map { LoginResult(it, null) } ?: Observable.error(IllegalArgumentException()))
                    .doOnNext { logonUser = it.person }

    override fun resumeLogin(token: Serializable): Observable<LoginResult> {
        throw UnsupportedOperationException()
    }

    override fun peekCurrentLogonUserId() = logonUser?.id

    override fun logout(): Observable<Unit> {
        logonUser = null
        return Observable.just(null)
    }

}