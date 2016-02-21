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

/**
 *
 * 所有本地数据库的实现
 *
 * Created by fanchao on 9/01/16.
 */

class LocalRepository(internal val db: Database)
: UserRepository
        , GroupRepository
        , RoomRepository
        , ContactRepository {

    // 查询数据库操作的线程池. 线程数 = CPU核心数
    private val queryScheduler = Schedulers.computation()

    // 写入数据库操作的线程池, 只有一个线程
    private val modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    private val tableSubjects = hashMapOf<String, PublishSubject<Unit?>>()

    // 用于存储当前事务下的表格表格变化通知. 所有事务中的通知都会缓存, 当事务完成后一起将通知发出
    private val pendingNotificationTables = object : ThreadLocal<HashSet<String>>() {
        override fun initialValue() = hashSetOf<String>()
    }

    // 指示当前线程是否处在一个事务下
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
    override fun getUser(id: String): Observable<User?> {
        return createQuery(User.TABLE_NAME, "SELECT * FROM ${User.TABLE_NAME} WHERE ${User.COL_ID} = ? LIMIT 1", id)
                .mapToOneOrDefault(User.MAPPER, null)
    }

    override fun getUsers(ids: Iterable<String>): Observable<List<User>> {
        return createQuery(User.TABLE_NAME, "SELECT * FROM ${User.TABLE_NAME} WHERE ${User.COL_ID} IN ${ids.toSqlSet(true)}")
                .mapToList(User.MAPPER)
    }

    override fun getGroup(groupId: String): Observable<Group?> {
        return createQuery(Group.TABLE_NAME, "SELECT * FROM ${Group.TABLE_NAME} WHERE ${Group.COL_ID} = ? LIMIT 1", groupId)
                .mapToOneOrDefault(Group.MAPPER, null)
    }

    override fun getGroupsWithMembers(groupIds: Iterable<String>, maxMember: Int): Observable<List<GroupWithMembers>> {
        return createQuery(listOf(Group.TABLE_NAME, User.TABLE_NAME), "SELECT G.*, U.* FROM ${GroupMembers.TABLE_NAME} AS GM " +
                "INNER JOIN ${Group.TABLE_NAME} AS G ON GM.${GroupMembers.COL_GROUP_ID} = G.${Group.COL_ID} " +
                "LEFT JOIN ${User.TABLE_NAME} AS U ON GM.${GroupMembers.COL_PERSON_ID} = U.${User.COL_ID} " +
                "WHERE ${GroupMembers.COL_GROUP_ID} IN ${groupIds.toSqlSet()}")
            .map {
                var currGroup : Group? = null
                var currMembers : MutableList<User> = arrayListOf()
                val result = arrayListOf<GroupWithMembers>()
                it.use {
                    while (it.moveToNext()) {
                        val groupId = it.getStringValue(Group.COL_ID)
                        if (currGroup == null) {
                            currGroup = Group().from(it)
                        }
                        else if (currGroup!!.id != groupId) {
                            result += GroupWithMembers(currGroup!!, currMembers)
                            currGroup = Group().from(it)
                            currMembers = arrayListOf()
                        }
                    }
                }

                if (currGroup != null) {
                    result += GroupWithMembers(currGroup!!, currMembers)
                }

                result
            }
    }

    override fun getAllUsers(): Observable<List<User>> {
        return createQuery(User.TABLE_NAME, "SELECT * FROM ${User.TABLE_NAME}")
                .mapToList(User.MAPPER)
    }

    override fun replaceAllUsers(users: Iterable<User>) = updateInTransaction {
        delete(User.TABLE_NAME, "")

        val cacheValues = hashMapOf<String, Any?>()
        users.forEach {
            insert(User.TABLE_NAME, cacheValues.apply { clear(); it.toValues(this) })
        }
    }

    override fun saveUser(user: User) = updateInTransaction {
        insert(User.TABLE_NAME, hashMapOf<String, Any?>().apply { user.toValues(this) })
        user
    }

    override fun getGroupMembers(groupId: String) = createQuery(
            listOf(Group.TABLE_NAME, User.TABLE_NAME),
            "SELECT P.* FROM " + User.TABLE_NAME + " AS P " + "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " + "GM." + GroupMembers.COL_PERSON_ID + " = P." + User.COL_ID + " " + "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?",
            groupId)
            .mapToList(User.MAPPER)

    override fun updateGroupMembers(groupId: String, memberIds: Iterable<String>) = updateInTransaction {
        delete(GroupMembers.TABLE_NAME, "${GroupMembers.COL_GROUP_ID} = ?", groupId)
        doAddGroupMembers(groupId, memberIds)
    }

    override fun clearRooms() = updateInTransaction {
        delete(Room.TABLE_NAME, "1")
        delete(RoomMembers.TABLE_NAME, "1")
    }

    override fun getRoom(roomId: String) =
            createQuery(Room.TABLE_NAME, "SELECT * FROM ${Room.TABLE_NAME} WHERE ${Room.COL_ID} = ? LIMIT 1", roomId)
                    .mapToOneOrDefault(Room.MAPPER, null)

    override fun getRoomMembers(roomId: String) =
            createQuery(listOf(Room.TABLE_NAME, RoomMembers.TABLE_NAME),
                    "SELECT P.* FROM ${User.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS CM ON CM.${RoomMembers.COL_USER_ID} = P.${User.COL_ID} WHERE CM.${RoomMembers.COL_ROOM_ID} = ?", roomId)
                    .mapToList(User.MAPPER)

    override fun getRoomWithMembers(roomId: String) = getRoom(roomId).flatMap { room : Room? ->
        if (room == null) {
            null.toObservable()
        }
        else {
            getRoomMembers(roomId).map { RoomWithMembers(room, it) }
        }
    }

    override fun updateRoom(room: Room, memberIds: Iterable<String>) = updateInTransaction {
        room.apply {
            val cacheValue = hashMapOf<String, Any?>()

            insert(Room.TABLE_NAME,
                    cacheValue.apply { clear(); room.toValues(this) },
                    true)

            db.delete(RoomMembers.TABLE_NAME, "${RoomMembers.COL_ROOM_ID} = ?", room.id)

            cacheValue.clear()
            cacheValue[RoomMembers.COL_ROOM_ID] = room.id
            memberIds.forEach {
                insert(RoomMembers.TABLE_NAME, cacheValue.apply { this[RoomMembers.COL_USER_ID] = it })
            }
        }
    }

    override fun updateRoomMembers(roomId: String, memberIds: Iterable<String>) = updateInTransaction {
        db.delete(RoomMembers.TABLE_NAME, "${RoomMembers.COL_ROOM_ID} = ?", roomId)
        val cacheContentValues = HashMap<String, Any?>(2).apply { put(RoomMembers.COL_ROOM_ID, roomId) }
        memberIds.forEach {
            insert(RoomMembers.TABLE_NAME, cacheContentValues.apply { put(RoomMembers.COL_USER_ID, it) })
        }
    }

    override fun getRoomWithMemberNames(roomId: String, maxMember: Int) =
            createQuery(listOf(Room.TABLE_NAME, RoomMembers.TABLE_NAME), "SELECT * FROM ${Room.TABLE_NAME} WHERE ${Room.COL_ID} = ?", roomId)
                    .mapToOneOrDefault(Func1<ResultSet, RoomWithMemberNames> {
                        val conversation = Room.MAPPER.call(it)
                        RoomWithMemberNames(
                                conversation,
                                db.query("SELECT P.${User.COL_NAME} FROM ${User.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS GM ON GM.${RoomMembers.COL_USER_ID} = P.${User.COL_ID} AND ${RoomMembers.COL_ROOM_ID} = ? LIMIT $maxMember", conversation.id).mapAndClose { it.getString(0) },
                                db.query("SELECT COUNT(${RoomMembers.COL_USER_ID}) FROM ${RoomMembers.TABLE_NAME} WHERE ${RoomMembers.COL_ROOM_ID} = ?", conversation.id).countAndClose()
                        )
                    }, null)


    override fun getRoomsWithMemberNames(maxMember: Int) =
            createQuery(listOf(Room.TABLE_NAME, RoomMembers.TABLE_NAME), "SELECT * FROM ${Room.TABLE_NAME}")
                    .mapToList(Func1<ResultSet, RoomWithMemberNames> {
                        val conversation = Room.MAPPER.call(it)
                        RoomWithMemberNames(
                                conversation,
                                db.query("SELECT P.${User.COL_NAME} FROM ${User.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS GM ON GM.${RoomMembers.COL_USER_ID} = P.${User.COL_ID} AND ${RoomMembers.COL_ROOM_ID} = ? LIMIT $maxMember", conversation.id).mapAndClose { it.getString(0) },
                                db.query("SELECT COUNT(${RoomMembers.COL_USER_ID}) FROM ${RoomMembers.TABLE_NAME} WHERE ${RoomMembers.COL_ROOM_ID} = ?", conversation.id).countAndClose()
                        )
                    })

    override fun getContactItems() = createQuery(Contacts.TABLE_NAME,
            "SELECT * FROM ${Contacts.TABLE_NAME}")
            .mapToList(Contacts.MAPPER)

    override fun searchContactItems(searchTerm: String): Observable<List<ContactItem>> {
        val formattedSearchTerm = "%$searchTerm%"
        return createQuery(Contacts.TABLE_NAME,
                "SELECT CI.* FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${User.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${User.COL_ID} LEFT JOIN ${Group.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Group.COL_ID} WHERE (P.${User.COL_NAME} LIKE ? OR G.${Group.COL_NAME} LIKE ? )", formattedSearchTerm, formattedSearchTerm)
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