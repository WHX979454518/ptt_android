package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import java.io.Serializable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by fanchao on 18/12/15.
 */

object MockPersons {
    val PERSON_1 = User("1", "person 1", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_2 = User("2", "person 2", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_3 = User("3", "person 3", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_4 = User("4", "person 4", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))
    val PERSON_5 = User("5", "person 5", EnumSet.of(Privilege.CREATE_GROUP, Privilege.MAKE_CALL))

    val ALL = listOf(PERSON_1, PERSON_2, PERSON_3, PERSON_4, PERSON_5)
}

object MockGroups {
    val GROUP_1 = Group("1", "Group 1")
    val GROUP_2 = Group("2", "Group 2")
    val GROUP_3 = Group("3", "Group 3")
    val GROUP_4 = Group("4", "Group 4")

    val GROUP_MEMBERS = mapOf(
            Pair(GROUP_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_2.id)),
            Pair(GROUP_2.id, listOf(MockPersons.PERSON_3.id, MockPersons.PERSON_4.id)),
            Pair(GROUP_3.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_4.id, MockPersons.PERSON_5.id)))

    val ALL = listOf(GROUP_1, GROUP_2, GROUP_3)
}

object MockConversations {
    val CONVERSATION_1 = Room("1", "Conversation 1", null, MockPersons.PERSON_1.id, false)
    val CONVERSATION_2 = Room("2", "Conversation 2", null, MockPersons.PERSON_2.id, false)

    val CONVERSATION_MEMBERS = mapOf(
            Pair(CONVERSATION_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_3.id)),
            Pair(CONVERSATION_2.id, listOf(MockPersons.PERSON_2.id, MockPersons.PERSON_4.id)))

    val ALL = listOf(CONVERSATION_1, CONVERSATION_2)
}

class MockSignalProvider(val currentUser: User,
                         val users: List<User>,
                         val groups: List<Group>,
                         val groupMembers: Map<String, Iterable<String>>) : AbstractSignalProvider() {

    private val idSeq = AtomicInteger(0)

    val conversations = HashMap<String, ConversationInfo>()

    override fun createRoom(request: JoinRoomFromContact): Observable<Room> {
        when (request) {
            is JoinRoomFromUser -> {
                // 寻找已有的会话
                val setToFind = hashSetOf(currentUser.id, request.userId)
                var foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                // 新建一个会话
                val conv = Room(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, ConversationInfo(conv, setToFind))
                return conv.toObservable()
            }

            is JoinRoomFromGroup -> {
                val members = groupMembers.get(request.groupId) ?: return Observable.error(IllegalArgumentException("Group ${request.groupId} does not exist"))

                val setToFind = members.toSet()
                val foundEntry = conversations.entries.firstOrNull { it.value.members.toHashSet() == setToFind }
                if (foundEntry != null) {
                    return foundEntry.value.conversation.toObservable()
                }

                val conv = Room(idSeq.incrementAndGet().toString(), "Conversation $idSeq", "Conversation description", currentUser.id, false)
                conversations.put(conv.id, ConversationInfo(conv, members.toHashSet()))
                return conv.toObservable()
            }

            else -> throw IllegalArgumentException("Unknown request: $request")
        }
    }

    override fun deleteRoom(roomId: String): Observable<Unit> {
        val roomInfo = conversations[roomId] ?: return Observable.just(null)

        if (roomInfo.room.ownerId != currentUser.id) {
            return Observable.error(IllegalAccessError())
        }

        conversations.remove(roomId)
        return Observable.just(null)
    }

    override fun joinRoom(roomId: String): Observable<RoomInfo> {
        val roomInfo = conversations[roomId] ?: return Observable.error(IllegalArgumentException())
        ensureActiveMemberSubject(roomId).onNext(peekActiveMemberIds(roomId) + currentUser.id)
        return RoomInfo(roomInfo.room.id, roomInfo.room.id, roomInfo.members, peekActiveMemberIds(roomId),
                peekCurrentSpeakerId(roomId), emptyMap()).toObservable()
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

    class ConversationInfo(val room: Room,
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
    var logonUser: User? = null

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