package com.xianzhitech.ptt.repo

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.db.Database
import com.xianzhitech.ptt.db.DatabaseFactory
import com.xianzhitech.ptt.db.ResultSet
import com.xianzhitech.ptt.db.TableDefinition
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.*
import rx.Observable
import rx.functions.Func1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.io.Closeable
import java.util.*
import java.util.concurrent.Executors

/**
 *
 * 所有本地数据库的实现
 *
 * Created by fanchao on 9/01/16.
 */

class LocalRepository(private val databaseFactory: DatabaseFactory)
: Closeable,
        UserRepository
        , GroupRepository
        , RoomRepository
        , ContactRepository {

    internal val db : Database = databaseFactory.createDatabase(arrayOf(Users, Groups, Rooms, GroupMembers, RoomMembers, Contacts), 1)

    // 查询数据库操作的线程池. 线程数 = CPU核心数
    internal val queryScheduler = Schedulers.computation()

    // 写入数据库操作的线程池, 只有一个线程
    internal val modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    internal val tableSubjects = hashMapOf<String, PublishSubject<Unit>>()

    // 用于存储当前事务下的表格表格变化通知. 所有事务中的通知都会缓存, 当事务完成后一起将通知发出
    internal val pendingNotificationTables : MutableSet<String> by threadLocal { hashSetOf<String>() }

    // 指示当前线程是否处在一个事务下
    internal var inTransaction : Boolean by threadLocal { false }

    internal fun getTableSubject(tableName: String): PublishSubject<Unit> {
        return synchronized(tableSubjects, {
            tableSubjects.getOrPut(tableName, { PublishSubject.create<Unit>() })
        })
    }

    internal fun <T> updateInTransaction(func: () -> T?): Observable<T> {
        return Observable.defer<T> {
            inTransaction = true
            try {
                db.executeInTransaction(func).toObservable()
            } finally {
                inTransaction = false
                pendingNotificationTables.apply {
                    forEach { getTableSubject(it).onNext(Unit) }
                    clear()
                }
            }
        }.subscribeOn(modifyScheduler)
    }

    internal fun createQuery(tableNames: Iterable<String>, sql: String, vararg args: Any?) =
            Observable.merge(tableNames.transform { getTableSubject(it) }).mergeWith(Observable.just(null))
                    .observeOn(queryScheduler)
                    .map { db.query(sql, *args) }

    internal fun createQuery(tableName: String, sql: String, vararg args: Any?) = createQuery(listOf(tableName), sql, *args)

    internal fun notifyTable(tableName: String) {
        if (inTransaction) {
            pendingNotificationTables.add(tableName)
        } else {
            getTableSubject(tableName).onNext(Unit)
        }
    }

    internal fun delete(tableName: String, whereClause: String, vararg args: Any?) {
        db.delete(tableName, whereClause, *args)
        notifyTable(tableName)
    }

    internal fun insert(tableName: String, values: Map<String, Any?>, replaceIfConflicts: Boolean = false) {
        db.insert(tableName, values, replaceIfConflicts)
        notifyTable(tableName)
    }


    @CheckResult
    override fun getUser(id: String): Observable<User?> {
        return createQuery(Users.TABLE_NAME, "SELECT * FROM ${Users.TABLE_NAME} WHERE ${Users.COL_ID} = ? LIMIT 1", id)
                .mapToOneOrDefault(Users.MAPPER, null)
    }

    override fun getUsers(ids: Iterable<String>): Observable<List<User>> {
        return createQuery(Users.TABLE_NAME, "SELECT * FROM ${Users.TABLE_NAME} WHERE ${Users.COL_ID} IN ${ids.toSqlSet(true)}")
                .mapToList(Users.MAPPER)
    }

    override fun getGroup(groupId: String): Observable<Group?> {
        return createQuery(Groups.TABLE_NAME, "SELECT * FROM ${Groups.TABLE_NAME} WHERE ${Groups.COL_ID} = ? LIMIT 1", groupId)
                .mapToOneOrDefault(Groups.MAPPER, null)
    }

    override fun getGroupsWithMembers(groupIds: Iterable<String>, maxMember: Int): Observable<List<GroupWithMembers>> {
        return createQuery(listOf(Groups.TABLE_NAME, GroupMembers.TABLE_NAME, Users.TABLE_NAME), "SELECT * FROM ${Groups.TABLE_NAME} WHERE ${Groups.COL_ID} IN ${groupIds.toSqlSet()}")
                .mapToList(Func1 {
                    GroupWithMembers(
                            createGroup(it),
                            db.query("SELECT * FROM ${Users.TABLE_NAME} AS U INNER JOIN ${GroupMembers.TABLE_NAME} AS GM ON GM.${GroupMembers.COL_PERSON_ID} = U.${Users.COL_ID} LIMIT $maxMember").mapAndClose { createUser(it) },
                            db.query("SELECT COUNT(*) FROM ${Users.TABLE_NAME} AS U INNER JOIN ${GroupMembers.TABLE_NAME} AS GM ON GM.${GroupMembers.COL_PERSON_ID} = U.${Users.COL_ID}").countAndClose())
                })
    }

    override fun getAllUsers(): Observable<List<User>> {
        return createQuery(Users.TABLE_NAME, "SELECT * FROM ${Users.TABLE_NAME}")
                .mapToList(Users.MAPPER)
    }

    override fun replaceAllUsers(users: Iterable<User>) = updateInTransaction {
        delete(Users.TABLE_NAME, "")

        val cacheValues = hashMapOf<String, Any?>()
        users.forEach {
            insert(Users.TABLE_NAME, cacheValues.apply { clear(); it.toValues(this) })
        }
    }

    override fun saveUser(user: User) = updateInTransaction {
        insert(Users.TABLE_NAME, hashMapOf<String, Any?>().apply { user.toValues(this) })
        user
    }

    override fun getGroupMembers(groupId: String) = createQuery(
            listOf(Groups.TABLE_NAME, Users.TABLE_NAME),
            "SELECT P.* FROM " + Users.TABLE_NAME + " AS P " + "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " + "GM." + GroupMembers.COL_PERSON_ID + " = P." + Users.COL_ID + " " + "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?",
            groupId)
            .mapToList(Users.MAPPER)

    override fun updateGroupMembers(groupId: String, memberIds: Iterable<String>) = updateInTransaction {
        delete(GroupMembers.TABLE_NAME, "${GroupMembers.COL_GROUP_ID} = ?", groupId)
        doAddGroupMembers(groupId, memberIds)
    }

    override fun clearRooms() = updateInTransaction {
        delete(Rooms.TABLE_NAME, "1")
        delete(RoomMembers.TABLE_NAME, "1")
    }

    override fun getRoom(roomId: String) =
            createQuery(Rooms.TABLE_NAME, "SELECT * FROM ${Rooms.TABLE_NAME} WHERE ${Rooms.COL_ID} = ? LIMIT 1", roomId)
                    .mapToOneOrDefault(Rooms.MAPPER, null)

    override fun getRoomMembers(roomId: String) =
            createQuery(listOf(Rooms.TABLE_NAME, RoomMembers.TABLE_NAME),
                    "SELECT P.* FROM ${Users.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS CM ON CM.${RoomMembers.COL_USER_ID} = P.${Users.COL_ID} WHERE CM.${RoomMembers.COL_ROOM_ID} = ?", roomId)
                    .mapToList(Users.MAPPER)

    override fun getRoomWithMembers(roomId: String, maxMember: Int) = getRoom(roomId).flatMap { room : Room? ->
        if (room == null) {
            null.toObservable()
        }
        else {
            getRoomMembers(roomId).map {
                RoomWithMembers(room,
                        db.query("SELECT P.* FROM ${Users.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS CM ON CM.${RoomMembers.COL_USER_ID} = P.${Users.COL_ID} WHERE CM.${RoomMembers.COL_ROOM_ID} = ? LIMIT $maxMember", roomId)
                                .mapAndClose { createUser(it) },
                        db.query("SELECT COUNT(*) FROM ${Users.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS CM ON CM.${RoomMembers.COL_USER_ID} = P.${Users.COL_ID} WHERE CM.${RoomMembers.COL_ROOM_ID} = ?", roomId)
                                .countAndClose()
                )
            }
        }
    }

    override fun updateRoom(room: Room, memberIds: Iterable<String>) = updateInTransaction {
        room.apply {
            val cacheValue = hashMapOf<String, Any?>()

            insert(Rooms.TABLE_NAME,
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

    override fun updateRoomLastActiveUser(roomId: String, activeUserId: String) = updateInTransaction {
        db.update(Rooms.TABLE_NAME,
                mapOf(Rooms.COL_LAST_ACTIVE_USER_ID.to(activeUserId), Rooms.COL_LAST_ACTIVE_TIME.to(System.currentTimeMillis())),
                "${Rooms.COL_ID} = ?",
                roomId)
        Unit
    }

    override fun getRoomsWithMembers(maxMember: Int) =
            createQuery(listOf(Rooms.TABLE_NAME, RoomMembers.TABLE_NAME), "SELECT * FROM ${Rooms.TABLE_NAME}")
                    .mapToList(Func1<ResultSet, RoomWithMembers> {
                        val conversation = Rooms.MAPPER.call(it)
                        RoomWithMembers(
                                conversation,
                                db.query("SELECT P.* FROM ${Users.TABLE_NAME} AS P INNER JOIN ${RoomMembers.TABLE_NAME} AS GM ON GM.${RoomMembers.COL_USER_ID} = P.${Users.COL_ID} AND ${RoomMembers.COL_ROOM_ID} = ? LIMIT $maxMember", conversation.id).mapAndClose { createUser(it) },
                                db.query("SELECT COUNT(${RoomMembers.COL_USER_ID}) FROM ${RoomMembers.TABLE_NAME} WHERE ${RoomMembers.COL_ROOM_ID} = ?", conversation.id).countAndClose()
                        )
                    })

    override fun getContactItems() = createQuery(Contacts.TABLE_NAME,
            "SELECT * FROM ${Contacts.TABLE_NAME}")
            .mapToList(Contacts.MAPPER)

    override fun searchContactItems(searchTerm: String): Observable<List<ContactItem>> {
        val formattedSearchTerm = "%$searchTerm%"
        return createQuery(Contacts.TABLE_NAME,
                "SELECT CI.* FROM ${Contacts.TABLE_NAME} AS CI LEFT JOIN ${Users.TABLE_NAME} AS P ON CI.${Contacts.COL_PERSON_ID} = P.${Users.COL_ID} LEFT JOIN ${Groups.TABLE_NAME} AS G ON CI.${Contacts.COL_GROUP_ID} = G.${Groups.COL_ID} WHERE (P.${Users.COL_NAME} LIKE ? OR G.${Groups.COL_NAME} LIKE ? )", formattedSearchTerm, formattedSearchTerm)
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
        delete(Groups.TABLE_NAME, "1")

        val values = HashMap<String, Any?>()
        groups.forEach {
            insert(Groups.TABLE_NAME, values.apply { clear(); it.toValues(this) })
            doAddGroupMembers(it.id, groupMembers[it.id] ?: emptyList())
        }
    }

    internal fun doAddGroupMembers(groupId: String, members: Iterable<String>) {
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

    override fun close() {
        db.close()
    }
}

internal object Contacts : TableDefinition {
    const val TABLE_NAME = "contacts";

    const val COL_GROUP_ID = "contact_group_id"
    const val COL_PERSON_ID = "contact_person_id"

    override val creationSql = "CREATE TABLE $TABLE_NAME (" +
            "$COL_GROUP_ID TEXT REFERENCES ${Groups.TABLE_NAME}(${Groups.COL_ID}) UNIQUE ON CONFLICT IGNORE," +
            "$COL_PERSON_ID TEXT REFERENCES ${Users.TABLE_NAME}(${Users.COL_ID}) UNIQUE ON CONFLICT IGNORE" +
            ")"

    @JvmField val MAPPER = Func1<ResultSet, ContactItem> { cursor ->
        var id = cursor?.optStringValue(COL_GROUP_ID)
        if (id?.isNotEmpty() ?: false) {
            return@Func1 GroupContactItem(id!!)
        }

        id = cursor?.optStringValue(COL_PERSON_ID)
        if (id?.isNotEmpty() ?: false) {
            return@Func1 UserContactItem(id!!)
        }

        throw IllegalArgumentException("ResultSet $cursor is not a valid contact")
    }
}


internal object Rooms : TableDefinition {

    const val TABLE_NAME = "rooms"

    const val COL_ID = "room_id"
    const val COL_NAME = "room_name"
    const val COL_DESC = "room_desc"
    const val COL_OWNER_ID = "room_owner_id"
    const val COL_IMPORTANT = "room_important"
    const val COL_LAST_ACTIVE_USER_ID = "room_last_active_user_id"
    const val COL_LAST_ACTIVE_TIME = "room_last_active_time"

    override val creationSql = "CREATE TABLE $TABLE_NAME (" +
            "$COL_ID TEXT PRIMARY KEY," +
            "$COL_NAME TEXT," +
            "$COL_DESC TEXT," +
            "$COL_OWNER_ID TEXT NOT NULL REFERENCES ${Users.TABLE_NAME}(${Users.COL_ID})," +
            "$COL_IMPORTANT INTEGER NOT NULL," +
            "$COL_LAST_ACTIVE_USER_ID TEXT," +
            "$COL_LAST_ACTIVE_TIME INTEGER" +
            ")"

    @JvmStatic val MAPPER = Func1<ResultSet, Room> {  createRoom(it) }
}

internal fun createRoom(cursor: ResultSet) : MutableRoom {
    return RoomImpl(id = cursor.getStringValue(Rooms.COL_ID),
            name = cursor.getStringValue(Rooms.COL_NAME),
            description = cursor.optStringValue(Rooms.COL_DESC),
            ownerId = cursor.getStringValue(Rooms.COL_OWNER_ID),
            important = cursor.getIntValue(Rooms.COL_IMPORTANT) != 0,
            lastActiveTime = cursor.getLongValue(Rooms.COL_LAST_ACTIVE_TIME).let { if (it > 0) Date(it) else null },
            lastActiveUserId = cursor.optStringValue(Rooms.COL_LAST_ACTIVE_USER_ID))
}

internal fun Room.toValues(values: MutableMap<String, Any?>) {
    values.put(Rooms.COL_ID, id)
    values.put(Rooms.COL_NAME, name)
    values.put(Rooms.COL_DESC, description)
    values.put(Rooms.COL_OWNER_ID, ownerId)
    values.put(Rooms.COL_IMPORTANT, important)
    values.put(Rooms.COL_LAST_ACTIVE_TIME, lastActiveTime?.time ?: 0)
    values.put(Rooms.COL_LAST_ACTIVE_USER_ID, lastActiveUserId)
}

internal fun MutableRoom.from(cursor: ResultSet): MutableRoom {
    id = cursor.getStringValue(Rooms.COL_ID)
    name = cursor.getStringValue(Rooms.COL_NAME)
    description = cursor.optStringValue(Rooms.COL_DESC)
    ownerId = cursor.getStringValue(Rooms.COL_OWNER_ID)
    important = cursor.getIntValue(Rooms.COL_IMPORTANT) != 0
    lastActiveTime = cursor.getLongValue(Rooms.COL_LAST_ACTIVE_TIME).let { if (it > 0) Date(it) else null }
    lastActiveUserId = cursor.optStringValue(Rooms.COL_LAST_ACTIVE_USER_ID)
    return this
}

internal object Groups : TableDefinition {
    const val TABLE_NAME = "groups"

    const val COL_ID = "group_id"
    const val COL_NAME = "group_name"
    const val COL_DESCRIPTION = "group_desc"

    override val creationSql = "CREATE TABLE $TABLE_NAME (" +
            "$COL_ID INTEGER PRIMARY KEY NOT NULL," +
            "$COL_NAME TEXT NOT NULL,$COL_DESCRIPTION TEXT" +
            ")"

    @JvmField val MAPPER = Func1<ResultSet, Group> { createGroup(it) }
}

internal fun Group.toValues(values : MutableMap<String, Any?>) {
    values.put(Groups.COL_ID, id)
    values.put(Groups.COL_DESCRIPTION, description)
    values.put(Groups.COL_NAME, name)
}

internal fun createGroup(cursor: ResultSet) : MutableGroup {
    return GroupImpl(id = cursor.getStringValue(Groups.COL_ID),
            description = cursor.optStringValue(Groups.COL_DESCRIPTION),
            name = cursor.getStringValue(Groups.COL_NAME),
            avatar = null)
}


internal object Users : TableDefinition {
    const val TABLE_NAME = "users"

    const val COL_ID = "person_id"
    const val COL_NAME = "person_name"
    const val COL_PRIV = "person_priv"
    const val COL_AVATAR = "person_avatar"

    override val creationSql = "CREATE TABLE $TABLE_NAME (" +
            "$COL_ID TEXT PRIMARY KEY NOT NULL, " +
            "$COL_NAME TEXT NOT NULL, " +
            "$COL_AVATAR TEXT, " +
            "$COL_PRIV INTEGER NOT NULL" +
            ")"

    @JvmField val MAPPER = Func1<ResultSet, User> {
        createUser(it)
    }
}

internal fun User.toValues(values: MutableMap<String, Any?>) {
    values.put(Users.COL_ID, id)
    values.put(Users.COL_AVATAR, avatar)
    values.put(Users.COL_NAME, name)
    values.put(Users.COL_PRIV, (this as? UserImpl)?.privilegesText ?: privileges.toDatabaseString())
}

internal fun createUser(cursor: ResultSet) : MutableUser {
    return UserImpl(id = cursor.getStringValue(Users.COL_ID),
            avatar = cursor.optStringValue(Users.COL_AVATAR),
            name = cursor.getStringValue(Users.COL_NAME),
            privilegesText = cursor.getStringValue(Users.COL_PRIV))
}


internal object GroupMembers : TableDefinition {
    const val TABLE_NAME = "group_members"

    const val COL_GROUP_ID = "gm_group_id"
    const val COL_PERSON_ID = "gm_person_id"

    override val creationSql = "CREATE TABLE $TABLE_NAME(" +
            "$COL_GROUP_ID TEXT NOT NULL REFERENCES ${Groups.TABLE_NAME}(${Groups.COL_ID}) ON DELETE CASCADE," +
            "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${Users.TABLE_NAME}(${Users.COL_ID}) ON DELETE CASCADE, " +
            "UNIQUE ($COL_GROUP_ID,$COL_PERSON_ID) ON CONFLICT REPLACE" +
            ")"
}

internal object RoomMembers : TableDefinition {
    const val TABLE_NAME = "room_members"

    const val COL_ROOM_ID = "rm_room_id"
    const val COL_USER_ID = "rm_user_id"

    override val creationSql = "CREATE TABLE $TABLE_NAME (" +
            "$COL_ROOM_ID TEXT NOT NULL REFERENCES ${Rooms.TABLE_NAME}(${Rooms.COL_ID})," +
            "$COL_USER_ID TEXT NOT NULL REFERENCES ${Users.TABLE_NAME}(${Users.COL_ID})" +
            ")"
}