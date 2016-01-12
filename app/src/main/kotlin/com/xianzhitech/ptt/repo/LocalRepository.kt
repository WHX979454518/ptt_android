package com.xianzhitech.ptt.repo

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.db.Database
import com.xianzhitech.ptt.db.ResultSet
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import rx.Observable
import rx.functions.Func1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.*

/**
 *
 * 所有本地数据库的实现
 *
 * Created by fanchao on 9/01/16.
 */

class LocalRepository(internal val db: Database)
: UserRepository
        , GroupRepository
        , ConversationRepository
        , ContactRepository {

    private val queryScheduler = Schedulers.computation()
    private val modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    private val tableSubjects = hashMapOf<String, PublishSubject<Unit?>>()
    private val pendingNotificationTables = object : ThreadLocal<HashSet<String>>() {
        override fun initialValue() = hashSetOf<String>()
    }
    private val inTransaction = object : ThreadLocal<Boolean>() {
        override fun initialValue() = false
    }

    private fun getTableSubject(tableName: String): PublishSubject<Unit?> {
        synchronized(tableSubjects, {
            return tableSubjects.getOrPut(tableName, { PublishSubject.create<Unit?>() })
        })
    }

    private fun <T> updateInTransaction(func: () -> T?): Observable<T> {
        return Observable.defer<T> {
            inTransaction.set(true)
            try {
                db.executeInTransaction(func).toObservable()
            } finally {
                inTransaction.set(false)
                pendingNotificationTables.get().apply {
                    forEach { getTableSubject(it).onNext(null) }
                    clear()
                }
            }
        }.subscribeOn(modifyScheduler)
    }

    private fun createQuery(tableNames: Iterable<String>, sql: String, vararg args: Any?) =
            Observable.merge(tableNames.transform { getTableSubject(it) }).mergeWith(Observable.just(null))
                    .flatMap {
                        Observable.defer<ResultSet> {
                            db.query(sql, *args).toObservable()
                        }.subscribeOn(queryScheduler)
                    }

    private fun createQuery(tableName: String, sql: String, vararg args: Any?) = createQuery(listOf(tableName), sql, *args)

    private fun notifyTable(tableName: String) {
        if (inTransaction.get()) {
            pendingNotificationTables.get().add(tableName)
        } else {
            getTableSubject(tableName).onNext(null)
        }
    }

    private fun delete(tableName: String, whereClause: String, vararg args: Any?) {
        db.delete(tableName, whereClause, *args)
        notifyTable(tableName)
    }

    private fun insert(tableName: String, values: Map<String, Any?>, replaceIfConflicts: Boolean = false) {
        db.insert(tableName, values, replaceIfConflicts)
        notifyTable(tableName)
    }


    @CheckResult
    override fun getUser(id: String): Observable<Person?> {
        return createQuery(Person.TABLE_NAME, "SELECT * FROM ${Person.TABLE_NAME} WHERE ${Person.COL_ID} = ? LIMIT 1", id)
                .mapToOneOrDefault(Person.MAPPER, null)
    }

    override fun getGroup(groupId: String): Observable<Group?> {
        return createQuery(Group.TABLE_NAME, "SELECT * FROM ${Group.TABLE_NAME} WHERE ${Group.COL_ID} = ? LIMIT 1", groupId)
                .mapToOneOrDefault(Group.MAPPER, null)
    }

    override fun getAllUsers(): Observable<List<Person>> {
        return createQuery(Person.TABLE_NAME, "SELECT * FROM ${Person.TABLE_NAME}")
                .mapToList(Person.MAPPER)
    }

    override fun replaceAllUsers(users: Iterable<Person>) = updateInTransaction {
        delete(Person.TABLE_NAME, "")

        val cacheValues = hashMapOf<String, Any?>()
        users.forEach {
            insert(Person.TABLE_NAME, cacheValues.apply { clear(); it.toValues(this) })
        }
    }

    override fun getGroupMembers(groupId: String) = createQuery(
            listOf(Group.TABLE_NAME, Person.TABLE_NAME),
            "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " + "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " + "GM." + GroupMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " " + "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?",
            groupId)
            .mapToList(Person.MAPPER)

    override fun updateGroupMembers(groupId: String, memberIds: Iterable<String>) = updateInTransaction {
        delete(GroupMembers.TABLE_NAME, "${GroupMembers.COL_GROUP_ID} = ? AND ${GroupMembers.COL_PERSON_ID} NOT IN ${memberIds.toSqlSet()}", groupId)
        doAddGroupMembers(groupId, memberIds)
    }

    override fun getConversation(convId: String) =
            createQuery(Conversation.TABLE_NAME, "SELECT * FROM ${Conversation.TABLE_NAME} WHERE ${Conversation.COL_ID} = ? LIMIT 1", convId)
                    .mapToOne(Conversation.MAPPER)

    override fun getConversationMembers(convId: String) =
            createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME),
                    "SELECT P.* FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS CM ON CM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} WHERE CM.${ConversationMembers.COL_CONVERSATION_ID} = ?", convId)
                    .mapToList(Person.MAPPER)

    override fun updateConversation(conversation: Conversation, memberIds: Iterable<String>) = updateInTransaction {
        conversation.apply {
            val cacheValue = hashMapOf<String, Any?>()

            insert(Conversation.TABLE_NAME,
                    cacheValue.apply { clear(); conversation.toValues(this) },
                    true)

            db.delete(ConversationMembers.TABLE_NAME, "${ConversationMembers.COL_CONVERSATION_ID} = ?", conversation.id)

            cacheValue.clear()
            cacheValue[ConversationMembers.COL_CONVERSATION_ID] = conversation.id
            memberIds.forEach {
                insert(ConversationMembers.TABLE_NAME, cacheValue.apply { this[ConversationMembers.COL_PERSON_ID] = it })
            }
        }
    }

    override fun updateConversationMembers(convId: String, memberIds: Iterable<String>) = updateInTransaction {
        db.delete(ConversationMembers.TABLE_NAME, "${ConversationMembers.COL_CONVERSATION_ID} = ?", convId)
        val cacheContentValues = HashMap<String, Any?>(2).apply { put(ConversationMembers.COL_CONVERSATION_ID, convId) }
        memberIds.forEach {
            insert(ConversationMembers.TABLE_NAME, cacheContentValues.apply { put(ConversationMembers.COL_PERSON_ID, it) })
        }
    }

    override fun getConversationsWithMemberNames(maxMember: Int) =
            createQuery(listOf(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME), "SELECT * FROM ${Conversation.TABLE_NAME}")
                    .mapToList(Func1<ResultSet, ConversationWithMemberNames> {
                        val conversation = Conversation.MAPPER.call(it)
                        ConversationWithMemberNames(
                                conversation,
                                db.query("SELECT P.${Person.COL_NAME} FROM ${Person.TABLE_NAME} AS P INNER JOIN ${ConversationMembers.TABLE_NAME} AS GM ON GM.${ConversationMembers.COL_PERSON_ID} = P.${Person.COL_ID} AND ${ConversationMembers.COL_CONVERSATION_ID} = ? LIMIT $maxMember", conversation.id).mapAndClose { it.getString(0) },
                                db.query("SELECT COUNT(${ConversationMembers.COL_PERSON_ID}) FROM ${ConversationMembers.TABLE_NAME} WHERE ${ConversationMembers.COL_CONVERSATION_ID} = ?", conversation.id).countAndClose()
                        )
                    })

    override fun getContactItems() = createQuery(Contacts.TABLE_NAME,
            "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID}")
            .mapToList(Contacts.MAPPER)

    override fun searchContactItems(searchTerm: String): Observable<List<ContactItem>> {
        val formattedSearchTerm = "%$searchTerm%"
        return createQuery(Contacts.TABLE_NAME,
                "SELECT * FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Person.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Person.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID} WHERE (P.${Person.COL_NAME} LIKE ? OR G.${Group.COL_NAME} LIKE ? )", formattedSearchTerm, formattedSearchTerm)
                .mapToList(Contacts.MAPPER)
    }

    override fun replaceAllContacts(userIds: Iterable<String>, groupIds: Iterable<String>) = updateInTransaction {
        delete(Contacts.TABLE_NAME, "1")

        val values = HashMap<String, Any?>(2)
        userIds.forEach {
            insert(Contacts.TABLE_NAME, values.apply { clear(); put(Contacts.COL_PERSON_ID, it) })
        }

        groupIds.forEach {
            insert(Contacts.TABLE_NAME, values.apply { clear(); put(Contacts.COL_GROUP_ID, it) })
        }
    }

    override fun replaceAllGroups(groups: Iterable<Group>, groupMembers: Map<String, Iterable<String>>) = updateInTransaction {
        delete(Group.TABLE_NAME, "1")

        val values = HashMap<String, Any?>()
        groups.forEach {
            insert(Group.TABLE_NAME, values.apply { clear(); it.toValues(this) })
            doAddGroupMembers(it.id, groupMembers[it.id] ?: emptyList())
        }
    }

    private fun doAddGroupMembers(groupId: String, members: Iterable<String>) {
        val values = HashMap<String, Any?>(2)
        members.forEach {
            insert(
                    GroupMembers.TABLE_NAME,
                    values.apply {
                        put(GroupMembers.COL_GROUP_ID, groupId)
                        put(GroupMembers.COL_PERSON_ID, it)
                    },
                    true)
        }
    }
}