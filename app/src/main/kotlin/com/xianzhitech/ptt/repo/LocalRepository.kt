package com.xianzhitech.ptt.repo

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.support.annotation.CheckResult
import com.xianzhitech.ptt.Database
import com.xianzhitech.ptt.ext.countAndClose
import com.xianzhitech.ptt.ext.mapAndClose
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.ext.toSqlSet
import com.xianzhitech.ptt.model.*
import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.Executors
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.listOf

/**
 *
 * 所有本地数据库的实现
 *
 * Created by fanchao on 9/01/16.
 */

class LocalRepository(private val db: Database)
: UserRepository
        , GroupRepository
        , ConversationRepository
        , ContactRepository {
    private val queryScheduler = Schedulers.computation()
    private val modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    private inline fun <T> updateInTransaction(crossinline func: () -> T?): Observable<T> {
        return Observable.defer<T> {
            db.newTransaction().use {
                func().apply {
                    it.markSuccessful()
                }.toObservable()
            }
        }.subscribeOn(modifyScheduler)
    }

    @CheckResult
    override fun getUser(id: String): Observable<Person?> {
        return db.createQuery(Person.TABLE_NAME, "SELECT * FROM ${Person.TABLE_NAME} WHERE ${Person.COL_ID} = ? LIMIT 1", id)
                .mapToOneOrDefault(Person.MAPPER, null)
                .subscribeOn(queryScheduler)
    }

    override fun getGroup(groupId: String): Observable<Group?> {
        return db.createQuery(Group.TABLE_NAME, "SELECT * FROM ${Group.TABLE_NAME} WHERE ${Group.COL_ID} = ? LIMIT 1", groupId)
                .mapToOne(Group.MAPPER)
                .subscribeOn(queryScheduler)
    }

    override fun replaceAllUsers(users: Iterable<Person>) = updateInTransaction {
        db.delete(Person.TABLE_NAME, "")

        val cacheValues = ContentValues()
        users.forEach {
            db.insert(Person.TABLE_NAME, cacheValues.apply { clear(); it.toValues(this) })
        }
    }

    override fun getGroupMembers(groupId: String) =
            db.createQuery(
                    listOf(Group.TABLE_NAME, Person.TABLE_NAME),
                    "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " + "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " + "GM." + GroupMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " " + "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?",
                    groupId).mapToList(Person.MAPPER)

    override fun updateGroupMembers(groupId: String, memberIds: Iterable<String>) = updateInTransaction {
        db.delete(GroupMembers.TABLE_NAME, "${GroupMembers.COL_GROUP_ID} = ? AND ${GroupMembers.COL_PERSON_ID} NOT IN ${memberIds.toSqlSet()}", groupId)
        doAddGroupMembers(groupId, memberIds)
    }

    override fun getConversation(convId: String) =
            db.createQuery(Conversation.TABLE_NAME, "SELECT * FROM ${Conversation.TABLE_NAME} WHERE ${Conversation.COL_ID} = ? LIMIT 1", convId)
                    .mapToOne(Conversation.MAPPER)
                    .subscribeOn(queryScheduler)

    override fun getConversationMembers(convId: String) =
            db.createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME),
                    "SELECT P.* FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS CM ON CM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} WHERE CM.${ConversationMembers.COL_CONVERSATION_ID} = ?", convId)
                    .mapToList(Person.MAPPER)
                    .subscribeOn(queryScheduler)

    override fun updateConversation(conversation: Conversation) = updateInTransaction {
        conversation.let {
            db.insert(Conversation.TABLE_NAME,
                    ContentValues().apply { it.toValues(this) },
                    SQLiteDatabase.CONFLICT_REPLACE)
            it
        }
    }

    override fun updateConversationMembers(convId: String, memberIds: Iterable<String>) = updateInTransaction {
        db.delete(ConversationMembers.TABLE_NAME, "${ConversationMembers.COL_CONVERSATION_ID} = ?", convId)
        val cacheContentValues = ContentValues(2)
        memberIds.forEach {
            db.insert(ConversationMembers.TABLE_NAME, cacheContentValues.apply { put(ConversationMembers.COL_PERSON_ID, it) })
        }
    }

    override fun getConversationsWithMemberNames(maxMember: Int) =
            db.createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME), "SELECT * FROM ${Conversation.TABLE_NAME}")
                    .mapToList {
                        val conversation = Conversation.MAPPER.call(it)
                        ConversationWithMemberNames(
                                conversation,
                                db.query("SELECT P.${Person.COL_NAME} FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS GM ON GM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} AND ${ConversationMembers.COL_CONVERSATION_ID} = ? LIMIT $maxMember", conversation.id).mapAndClose { it.getString(0) },
                                db.query("SELECT COUNT(${ConversationMembers.COL_PERSON_ID}) FROM ${ConversationMembers.TABLE_NAME} WHERE ${ConversationMembers.COL_CONVERSATION_ID} = ?", conversation.id).countAndClose()
                        )
                    }
                    .subscribeOn(queryScheduler)

    override fun getContactItems() = db.createQuery(Contacts.TABLE_NAME,
            "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID}")
            .mapToList(Contacts.MAPPER)
            .subscribeOn(queryScheduler)

    override fun searchContactItems(searchTerm: String): Observable<List<ContactItem>> {
        val formattedSearchTerm = "%$searchTerm%"
        return db.createQuery(Contacts.TABLE_NAME,
                "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID} WHERE (P.${Person.COL_NAME} LIKE ? OR G.${Group.COL_NAME} LIKE ? )", formattedSearchTerm, formattedSearchTerm)
                .mapToList(Contacts.MAPPER)
                .subscribeOn(queryScheduler)
    }

    override fun replaceAllContacts(userIds: Iterable<String>, groupIds: Iterable<String>) = updateInTransaction {
        db.delete(Contacts.TABLE_NAME, "1")

        val values = ContentValues(2)
        userIds.forEach {
            db.insert(Contacts.TABLE_NAME, values.apply { clear(); put(Contacts.COL_PERSON_ID, it) })
        }

        groupIds.forEach {
            db.insert(Contacts.TABLE_NAME, values.apply { clear(); put(Contacts.COL_GROUP_ID, it) })
        }
    }

    override fun replaceAllGroups(groups: Iterable<Group>, groupMembers: Map<String, Iterable<String>>) = updateInTransaction {
        db.delete(Group.TABLE_NAME, "1")

        val values = ContentValues()
        groups.forEach {
            db.insert(Group.TABLE_NAME, values.apply { clear(); it.toValues(this) })
            doAddGroupMembers(it.id, groupMembers[it.id] ?: emptyList())
        }
    }

    private fun doAddGroupMembers(groupId: String, members: Iterable<String>) {
        val values = ContentValues(2)
        members.forEach {
            db.insert(
                    GroupMembers.TABLE_NAME,
                    values.apply {
                        put(GroupMembers.COL_GROUP_ID, groupId)
                        put(GroupMembers.COL_PERSON_ID, it)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}