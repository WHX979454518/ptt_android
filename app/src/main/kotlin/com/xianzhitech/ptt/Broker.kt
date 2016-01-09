package com.xianzhitech.ptt

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.support.annotation.CheckResult
import com.xianzhitech.ptt.ext.countAndClose
import com.xianzhitech.ptt.ext.mapAndClose
import com.xianzhitech.ptt.ext.toSqlSet
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.*
import rx.Observable
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.plusAssign
import kotlin.text.isNullOrEmpty

/**
 * 提供数据库操作层
 */
class Broker(internal val db: Database) {

    private val queryScheduler = Schedulers.computation()
    private val modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    private fun <T> updateInTransaction(func: () -> T?): Observable<T> {
        return Observable.defer<T> {
            db.newTransaction().use {
                val result = func()
                it.markSuccessful()
                return@defer Observable.just(result)
            }
        }.subscribeOn(modifyScheduler)
    }


    @CheckResult
    fun getPersons(ids: Iterable<String>): Observable<List<Person>> {
        return db.createQuery(Person.TABLE_NAME, "SELECT * FROM ${Person.TABLE_NAME} WHERE ${Person.COL_ID} IN ${ids.toSqlSet()}")
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler)
    }


    @CheckResult
    fun getConversation(id: String): Observable<Conversation> {
        return db.createQuery(Conversation.TABLE_NAME, "SELECT * FROM ${Conversation.TABLE_NAME} WHERE ${Conversation.COL_ID} = ? LIMIT 1", id)
                .mapToOne(Conversation.MAPPER)
                .subscribeOn(queryScheduler)
    }

    @CheckResult
    fun getConversationMembers(id: String): Observable<out Collection<Person>> {
        return db.createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME),
                "SELECT P.* FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS CM ON CM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} WHERE CM.${ConversationMembers.COL_CONVERSATION_ID} = ?", id)
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler)
    }

    val groups: Observable<List<Group>>
        @CheckResult
        get() = db.createQuery(Group.TABLE_NAME, "SELECT * FROM ${Group.TABLE_NAME}")
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler)

    @CheckResult
    fun getGroupsWithMemberNames(maxMember: Int): Observable<List<AggregateInfo<Group, String>>> {
        return db.createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME, GroupMembers.TABLE_NAME),
                "SELECT * FROM ${Group.TABLE_NAME}")
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler)
                .map({ groups ->
                    ArrayList<AggregateInfo<Group, String>>(groups.size).let {
                        groups.forEach { group ->
                            it += AggregateInfo(
                                    group,
                                    db.query("SELECT P.${Person.COL_NAME} FROM ${Person.TABLE_NAME} AS P INNER JOIN ${GroupMembers.TABLE_NAME} AS GM ON GM.${GroupMembers.COL_PERSON_ID} = P.${Person.COL_ID} AND ${GroupMembers.COL_GROUP_ID} = ? LIMIT $maxMember", group.id)
                                            .mapAndClose { it.getString(0) },
                                    db.query("SELECT COUNT(${GroupMembers.COL_PERSON_ID}) FROM ${GroupMembers.TABLE_NAME} WHERE ${GroupMembers.COL_GROUP_ID} = ?", group.id)
                                            .countAndClose())
                        }

                        it
                    }
                })
    }

    @CheckResult
    fun getConversationsWithMemberNames(maxMember: Int): Observable<List<AggregateInfo<Conversation, String>>> {
        return db.createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME), "SELECT * FROM ${Conversation.TABLE_NAME}")
                .mapToList(Conversation.MAPPER)
                .subscribeOn(queryScheduler)
                .map({ conversations ->
                    ArrayList<AggregateInfo<Conversation, String>>(conversations.size).let {
                        conversations.forEach { conversation ->
                            it += AggregateInfo(
                                    conversation,
                                    db.query("SELECT P.${Person.COL_NAME} FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS GM ON GM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} AND ${ConversationMembers.COL_CONVERSATION_ID} = ? LIMIT $maxMember", conversation.id)
                                            .mapAndClose { it.getString(0) },
                                    db.query("SELECT COUNT(${ConversationMembers.COL_PERSON_ID}) FROM ${ConversationMembers.TABLE_NAME} WHERE ${ConversationMembers.COL_CONVERSATION_ID} = ?", conversation.id)
                                            .countAndClose())
                        }
                        it
                    }
                })
    }

    @CheckResult
    fun getGroupMembers(groupId: String): Observable<List<Person>> {
        val sql = "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " + "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " + "GM." + GroupMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " " + "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?"


        return db.createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME), sql, groupId).mapToList(Person.MAPPER).subscribeOn(queryScheduler)
    }

    @CheckResult
    fun updateAllGroups(groups: Iterable<Group>?, groupMembers: Map<String, Iterable<String>>?): Observable<Unit> {
        return updateInTransaction({

            // Replace all existing groups
            if (groups != null) {
                val contentValues = ContentValues()
                val i = 0
                for (group in groups) {
                    contentValues.clear()
                    group.toValues(contentValues)
                    db.insert(Group.TABLE_NAME, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Delete remaining groups.
            if (groups == null) {
                db.delete(Group.TABLE_NAME, "1")
            } else {
                db.delete(Group.TABLE_NAME, "${Group.COL_ID} NOT IN ${groups.transform { it.id }.toSqlSet()}")
            }

            // Replace all group members
            if (groupMembers != null) {
                for (entry in groupMembers.entries) {
                    doUpdateGroupMembers(entry.key, entry.value)
                }
            }
        })
    }

    @CheckResult
    fun updateGroupMembers(groupId: String, groupMembers: Iterable<String>): Observable<Unit> {
        return updateInTransaction({
            doUpdateGroupMembers(groupId, groupMembers)
        })
    }

    @CheckResult
    fun updateConversationMembers(conversationId: String, groupMembers: Iterable<String>): Observable<Unit> {
        return updateInTransaction({
            db.delete(ConversationMembers.TABLE_NAME, "${ConversationMembers.COL_CONVERSATION_ID} = ?", conversationId)

            val values = ContentValues(2)
            values.put(ConversationMembers.COL_CONVERSATION_ID, conversationId)
            groupMembers.forEach { memberId ->
                values.put(ConversationMembers.COL_PERSON_ID, memberId)
                db.insert(ConversationMembers.TABLE_NAME, values)
            }
        })
    }

    @CheckResult
    fun getContacts(searchTerm: String?): Observable<List<ContactItem>> {
        return if (searchTerm.isNullOrEmpty()) {
            db.createQuery(Contacts.TABLE_NAME,
                    "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID}")
        } else {
            val formattedSearchTerm = "%$searchTerm%"
            db.createQuery(Contacts.TABLE_NAME,
                    "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID} WHERE (P.${Person.COL_NAME} LIKE ? OR G.${Group.COL_NAME} LIKE ? )", formattedSearchTerm, formattedSearchTerm)
        }
                .mapToList(Contacts.MAPPER)
                .subscribeOn(queryScheduler)
    }


    @CheckResult
    fun updateAllContacts(persons: Iterable<String>?, groups: Iterable<String>?): Observable<Unit> {
        return updateInTransaction({
            db.execute("DELETE FROM ${Contacts.TABLE_NAME} WHERE 1")
            val values = ContentValues()
            persons?.forEach { personId ->
                values.clear()
                values.put(Contacts.COL_PERSON_ID, personId)
                db.insert(Contacts.TABLE_NAME, values)
            }

            groups?.forEach { groupId ->
                values.clear()
                values.put(Contacts.COL_GROUP_ID, groupId)
                db.insert(Contacts.TABLE_NAME, values)
            }
        })
    }

    @CheckResult
    fun addGroupMembers(group: Group, persons: Iterable<Person>): Observable<Unit> {
        return updateInTransaction({
            doAddGroupMembers(group.id, persons.transform { it.id })
        })
    }

    @CheckResult
    fun updateAllPersons(persons: Iterable<Person>): Observable<Unit> {
        return updateInTransaction({
            db.delete(Person.TABLE_NAME, "")

            ContentValues().let {
                persons.forEach { person ->
                    it.clear()
                    person.toValues(it)
                    db.insert(Person.TABLE_NAME, it, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        })
    }

    @CheckResult
    fun saveConversation(conversation: Conversation): Observable<Conversation> {
        return updateInTransaction({
            val values = ContentValues()
            conversation.toValues(values)
            db.insert(Conversation.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE)
            conversation
        })
    }

    private fun doAddGroupMembers(groupId: String, members: Iterable<String>) {
        val values = ContentValues(2)

        for (memberId in members) {
            values.put(GroupMembers.COL_GROUP_ID, groupId)
            values.put(GroupMembers.COL_PERSON_ID, memberId)
            db.insert(GroupMembers.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun doUpdateGroupMembers(groupId: String, members: Iterable<String>) {
        doAddGroupMembers(groupId, members)

        db.delete(GroupMembers.TABLE_NAME, "${GroupMembers.COL_GROUP_ID} = ? AND ${GroupMembers.COL_PERSON_ID} NOT IN ${members.toSqlSet()}", groupId)
    }

    data class AggregateInfo<T1, T2>(val group: T1, val members: List<T2>, val memberCount: Int)
}
