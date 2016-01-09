package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Person
import rx.Observable
import kotlin.collections.forEachIndexed

/**
 *
 * 所有数据来源的接口
 *
 * Created by fanchao on 9/01/16.
 */

interface UserRepository {
    fun getUser(id: String): Observable<Person?>
    fun replaceAllUsers(users: Iterable<Person>): Observable<Unit>
}

interface GroupRepository {
    fun getGroup(groupId: String): Observable<Group?>
    fun getGroupMembers(groupId: String): Observable<List<Person>>
    fun updateGroupMembers(groupId: String, memberIds: Iterable<String>): Observable<Unit>
    fun replaceAllGroups(groups: Iterable<Group>, groupMembers: Map<String, Iterable<String>>): Observable<Unit>
}

interface ConversationRepository {
    fun getConversation(convId: String): Observable<Conversation?>
    fun getConversationMembers(convId: String): Observable<List<Person>>
    fun updateConversation(conversation: Conversation): Observable<Conversation>
    fun updateConversationMembers(convId: String, memberIds: Iterable<String>): Observable<Unit>
    fun getConversationsWithMemberNames(maxMember: Int): Observable<ConversationsWithMemberNames>
}

interface ContactRepository {
    fun getContactItems(): Observable<List<ContactItem>>
    fun searchContactItems(searchTerm: String): Observable<List<ContactItem>>
    fun replaceAllContacts(userIds: Iterable<String>, groupIds: Iterable<String>): Observable<Unit>
}

data class ConversationsWithMemberNames(val conversations: List<Conversation>,
                                        val memberNames: List<List<String>>,
                                        val memberCounts: List<Int>) {
    inline fun forEach(func: (Conversation, List<String>, Int) -> Unit) {
        conversations.forEachIndexed { i, conversation -> func(conversation, memberNames[i], memberCounts[i]) }
    }
}