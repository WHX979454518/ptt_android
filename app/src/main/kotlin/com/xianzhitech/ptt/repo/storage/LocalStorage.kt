package com.xianzhitech.ptt.repo.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.lazySplit
import com.xianzhitech.ptt.ext.perf
import com.xianzhitech.ptt.ext.toSqlSet
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.repo.RoomModel
import org.json.JSONObject
import org.slf4j.LoggerFactory
import rx.Completable
import rx.Single
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

private val logger = LoggerFactory.getLogger("LocalStorage")


class UserSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage<User>(db), UserStorage {
    override fun getUsers(ids: Iterable<String>, out: MutableList<User>) = logger.perf("Getting users") {
        read<List<User>>(ids, out, {
            queryList(Users.MAPPER, "SELECT ${Users.ALL} FROM ${Users.TABLE_NAME} WHERE ${Users.ID} IN ${it.toSqlSet()}")
        })
    }

    override fun saveUsers(users: Iterable<User>) : Completable = executeInTransaction {
        val contentValues = ContentValues()
        users.forEach {
            db.insertWithOnConflict(Users.TABLE_NAME, null, it.toContentValues(contentValues), SQLiteDatabase.CONFLICT_REPLACE)
        }
        clearCache(users)
    }

    override fun clear() : Completable = executeInTransaction {
        db.delete(Users.TABLE_NAME, "1", emptyArray())
        clearCache()
    }
}

class GroupSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage<Group>(db), GroupStorage {
    override fun getGroups(groupIds: Iterable<String>, out: MutableList<Group>) = logger.perf("Getting groups") {
        read<List<Group>>(groupIds, out, {
            queryList(Groups.MAPPER, "SELECT ${Groups.ALL} FROM ${Groups.TABLE_NAME} WHERE ${Groups.ID} IN ${it.toSqlSet()}")
        })
    }

    override fun saveGroups(groups: Iterable<Group>) : Completable = executeInTransaction {
        val contentValues = ContentValues()
        groups.forEach {
            db.insertWithOnConflict(Groups.TABLE_NAME, null, it.toContentValues(contentValues), SQLiteDatabase.CONFLICT_REPLACE)
        }
        clearCache(groups)
    }

    override fun clear() : Completable = executeInTransaction {
        db.delete(Groups.TABLE_NAME, "1", emptyArray())
        clearCache()
    }
}

class RoomSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage<RoomModel>(db), RoomStorage {
    private var roomIds : HashSet<String>? = null
    private val roomIdsLock = ReentrantReadWriteLock()

    private fun ensureRoomIds() : Single<List<String>> {
        return Single.defer {
            val result = roomIdsLock.read { roomIds?.toList() }
            if (result == null) {
                Single.fromCallable<List<String>> {
                    logger.perf("Caching all room ids") {
                        db.rawQuery("SELECT ${Rooms.ID} FROM ${Rooms.TABLE_NAME}", emptyArray()).use {
                            val roomIdList = ArrayList<String>(it.count).apply {
                                if (it.moveToFirst()) {
                                    do {
                                        add(it.getString(0))
                                    } while (it.moveToNext())
                                }
                            }

                            roomIdsLock.write {
                                if (roomIds == null) {
                                    roomIds = roomIdList.toHashSet()
                                } else {
                                    roomIds!!.addAll(roomIdList)
                                }
                            }

                            roomIdList
                        }
                    }
                }.subscribeOn(queryScheduler)
            }
            else {
                Single.just(result)
            }
        }
    }

    override fun getAllRooms() : Single<List<RoomModel>> {
        return logger.perf("getAllRooms") { ensureRoomIds().flatMap { getRooms(it, ArrayList(it.size)) } }
    }

    override fun getRooms(roomIds: Iterable<String>, out: MutableList<RoomModel>) = logger.perf("getRooms") {
        read<List<RoomModel>>(roomIds, out, {
            queryList(Rooms.MAPPER, "SELECT ${Rooms.ALL} FROM ${Rooms.TABLE_NAME} WHERE ${Rooms.ID} IN ${it.toSqlSet()}")
        })
    }

    override fun removeRooms(roomIds: Iterable<String>) : Completable = executeInTransaction {
        clearCacheById(roomIds)
        roomIdsLock.write { this.roomIds?.removeAll(roomIds) }
        db.delete(Rooms.TABLE_NAME, "${Rooms.ID} IN ${roomIds.toSqlSet()}", emptyArray())
    }

    override fun updateRoomName(roomId: String, name: String) : Completable = executeInTransaction {
        clearCacheById(listOf(roomId))
        val contentValue = ContentValues(2)
        contentValue.put(Rooms.NAME, name)
        contentValue.put(Rooms.LAST_ACTIVE_TIME, System.currentTimeMillis())
        db.update(Rooms.TABLE_NAME, contentValue, "${Rooms.ID} = ?", arrayOf(roomId))
    }

    override fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String) : Completable = executeInTransaction {
        val contentValue = ContentValues(3)
        contentValue.put(Rooms.LAST_SPEAK_TIME, time.time)
        contentValue.put(Rooms.LAST_SPEAK_MEMBER_ID, speakerId)
        contentValue.put(Rooms.LAST_ACTIVE_TIME, time.time)
        db.update(Rooms.TABLE_NAME, contentValue, "${Rooms.ID} = ?", arrayOf(roomId))
        clearCacheById(listOf(roomId))
    }

    override fun updateLastActiveTime(roomId: String, time: Date) : Completable = executeInTransaction {
        val contentValue = ContentValues(1)
        contentValue.put(Rooms.LAST_ACTIVE_TIME, time.time)
        db.update(Rooms.TABLE_NAME, contentValue, "${Rooms.ID} = ?", arrayOf(roomId))
        clearCacheById(listOf(roomId))
    }

    override fun saveRooms(rooms: Iterable<Room>) : Completable = executeInTransaction {
        val contentValues = ContentValues()
        rooms.forEach {
            if (db.update(Rooms.TABLE_NAME, it.toContentValues(contentValues), "${Rooms.ID} = ?", arrayOf(it.id)) <= 0) {
                contentValues.put(Rooms.LAST_ACTIVE_TIME, System.currentTimeMillis())
                db.insert(Rooms.TABLE_NAME, null, contentValues)
            }
        }
        roomIdsLock.write {
            if (roomIds != null) {
                rooms.forEach { roomIds!!.add(it.id) }
            }
        }
        clearCache(rooms)
    }

    override fun clear() : Completable = executeInTransaction {
        db.delete(Rooms.TABLE_NAME, "1", emptyArray())
        roomIdsLock.write { roomIds = null }
        clearCache()
    }
}

class ContactSQLiteStorage(db: SQLiteOpenHelper,
                           private val userStorage: UserStorage,
                           private val groupStorage: GroupStorage) : BaseSQLiteStorage<NamedModel>(db), ContactStorage {
    override fun getContactItems(): Single<List<NamedModel>> {
        return Single.fromCallable {
            logger.perf("Getting contact items") {
                // Get user ids and group ids
                val groupIds = db.rawQuery("SELECT ${Contacts.GROUP_ID} FROM ${Contacts.TABLE_NAME} WHERE ${Contacts.GROUP_ID} IS NOT NULL", emptyArray())?.use { cursor ->
                    ArrayList<String>(cursor.count).apply {
                        if (cursor.moveToFirst()) {
                            do {
                                add(cursor.getString(0))
                            } while (cursor.moveToNext())
                        }
                    }
                } ?: emptyList<String>()

                // Query user ids
                val userIds = db.rawQuery("SELECT ${Contacts.USER_ID} FROM ${Contacts.TABLE_NAME} WHERE ${Contacts.USER_ID} IS NOT NULL", emptyArray())?.use { cursor ->
                    ArrayList<String>(cursor.count).apply {
                        if (cursor.moveToFirst()) {
                            do {
                                add(cursor.getString(0))
                            } while (cursor.moveToNext())
                        }
                    }
                } ?: emptyList<String>()

                groupIds to userIds
            }
        }.subscribeOn(queryScheduler)
                .flatMap {
                    val (groupIds, userIds) = it
                    val result = ArrayList<NamedModel>(groupIds.size + userIds.size)
                    Single.zip(
                            userStorage.getUsers(ids = userIds, out = Collections.synchronizedList(result as MutableList<User>)),
                            groupStorage.getGroups(groupIds = groupIds, out = Collections.synchronizedList(result as MutableList<Group>)),
                            { users, groups -> result }
                    )
                }
    }

    override fun getAllContactUsers(): Single<List<User>> {
        return Single.defer {
            val ids = arrayListOf<String>()

            // Query user ids
            db.rawQuery("SELECT ${Contacts.USER_ID} FROM ${Contacts.TABLE_NAME} WHERE ${Contacts.USER_ID} IS NOT NULL", emptyArray())?.use { cursor ->
                ids.ensureCapacity(ids.size + cursor.count)
                if (cursor.moveToFirst()) {
                    do {
                        ids.add(cursor.getString(0))
                    } while (cursor.moveToNext())
                }
            }

            userStorage.getUsers(ids, ArrayList(ids.size))
        }
    }

    override fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>) : Completable {
        return Completable.merge(userStorage.saveUsers(users), groupStorage.saveGroups(groups))
                .andThen(executeInTransaction {
                    val localDb = db

                    localDb.delete(Contacts.TABLE_NAME, "1", emptyArray())
                    val contentValues = ContentValues(2)

                    users.forEach {
                        contentValues.put(Contacts.USER_ID, it.id)
                        localDb.insertWithOnConflict(Contacts.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
                    }

                    contentValues.remove(Contacts.USER_ID)
                    groups.forEach {
                        contentValues.put(Contacts.GROUP_ID, it.id)
                        localDb.insertWithOnConflict(Contacts.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                })
    }

    override fun clear() : Completable {
        return executeInTransaction {
            db.delete(Contacts.TABLE_NAME, "1", emptyArray())
        }
    }
}

class MessageSQLiteStorage(dbOpenHelper: SQLiteOpenHelper) : BaseSQLiteStorage<Message>(dbOpenHelper), MessageStorage {
    override fun saveMessages(messages: Iterable<Message>): Single<List<Message>> {
        val ids = arrayListOf<Long>()

        return executeInTransaction {
            val v = ContentValues()
            messages.forEach {
                v.clear()
                it.toContentValues(v)
                ids.add(db.insertWithOnConflict(Messages.TABLE_NAME, "", v, SQLiteDatabase.CONFLICT_REPLACE))
            }
        }.andThen(Single.fromCallable { queryList(Messages.MAPPER, "SELECT ${Messages.ALL} FROM ${Messages.TABLE_NAME} WHERE ${Messages.DB_ID} IN (${ids.toSqlSet()})") })
    }

    override fun getMessages(roomId: String, latestId: String?, count: Int): Single<List<Message>> {
        val extraCriteria : String

        if (latestId != null) {
            extraCriteria = "AND ${Messages.SEND_TIME} < (SELECT ${Messages.SEND_TIME} FROM ${Messages.TABLE_NAME} WHERE ${Messages.ID} = '$latestId')"
        }
        else {
            extraCriteria = ""
        }

        return Single.fromCallable {
            queryList(Messages.MAPPER,
                    "SELECT ${Messages.ALL} FROM ${Messages.TABLE_NAME} " +
                            "WHERE ${Messages.ROOM_ID} = '$roomId' $extraCriteria " +
                            "ORDER BY ${Messages.SEND_TIME} DESC " +
                            "LIMIT $count"
            )
        }.subscribeOn(queryScheduler)
    }

    override fun clear(): Completable {
        return executeInTransaction {
            db.delete(Messages.TABLE_NAME, "1", emptyArray())
        }
    }
}

private object Messages {
    const val TABLE_NAME = "messages"

    const val DB_ID = "db_id"
    const val ID = "id"
    const val SENDER_ID = "sender_id"
    const val SEND_TIME = "send_time"
    const val ROOM_ID = "room_id"
    const val READ = "read"
    const val TYPE = "type"
    const val BODY = "body"

    const val ALL = "$ID,$SENDER_ID,$SEND_TIME,$ROOM_ID,$TYPE,$BODY,$READ"
    const val CREATE_SQL = "CREATE TABLE $TABLE_NAME (" +
            "$DB_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$ID TEXT UNIQUE ON CONFLICT REPLACE," +
            "$SEND_TIME INTEGER NOT NULL," +
            "$READ INTEGER DEFAULT 0," +
            "$ROOM_ID TEXT REFERENCES ${Rooms.TABLE_NAME}(${Rooms.ID}) ON DELETE CASCADE," +
            "$SENDER_ID TEXT," +
            "$TYPE TEXT," +
            "$BODY TEXT" +
            "); " +
            "CREATE INDEX IF NOT EXISTS message_room_id_index ON $TABLE_NAME ($ROOM_ID); " +
            "CREATE INDEX IF NOT EXISTS message_id_index ON $TABLE_NAME ($ID); " +
            "CREATE INDEX IF NOT EXISTS message_sender_id_index ON $TABLE_NAME ($SENDER_ID); "

    val MAPPER: (Cursor) -> Message = {
        Message(
                id = it.getString(0),
                senderId = it.getString(1),
                sendTime =  it.getLong(2),
                roomId = it.getString(3),
                type = it.getString(4),
                body = JSONObject(it.getString(5)),
                read = it.getInt(6) != 0
        )
    }
}

private fun Message.toContentValues(v: ContentValues) {
    v.put(Messages.ID, id)
    v.put(Messages.SENDER_ID, senderId)
    v.put(Messages.SEND_TIME, sendTime)
    v.put(Messages.ROOM_ID, roomId)
    v.put(Messages.TYPE, type)
    v.put(Messages.BODY, body.toString())
}


open class BaseSQLiteStorage<T : Model>(dbOpenHelper: SQLiteOpenHelper) {

    protected val db: SQLiteDatabase by lazy { dbOpenHelper.writableDatabase }
    protected val cache = CacheMap<String, T>(1024)
    protected val cacheLock = ReentrantLock()
    protected val queryScheduler = Schedulers.computation()

    protected fun executeInTransaction(func: () -> Unit) : Completable {
        return Completable.fromCallable {
            db.beginTransaction()
            try {
                func()
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.subscribeOn(queryScheduler)
    }

    protected fun clearCacheById(ids : Iterable<String>) {
        cacheLock.withLock {
            ids.forEach { cache.remove(it) }
        }
    }

    protected fun clearCache(models : Iterable<NamedModel>) {
        cacheLock.withLock {
            models.forEach { cache.remove(it.id) }
        }
    }

    protected fun clearCache() {
        cacheLock.withLock {
            cache.clear()
        }
    }

    protected fun saveInCache(data : Iterable<T>) {
        cacheLock.withLock {
            data.forEach {
                cache[it.id] = it
            }
        }
    }

    protected fun <R : Collection<T>> read(ids: Iterable<String>,
                                           output : MutableCollection<T>,
                                           readFromDatabase : (Iterable<String>) -> List<T>) : Single<R> {
        return Single.defer<R> {
            val dbIds = arrayListOf<String>()
            cacheLock.withLock {
                ids.forEach { id ->
                    val obj = cache[id]
                    if (obj == null) {
                        dbIds.add(id)
                    }
                    else {
                        output.add(obj)
                    }
                }
            }

            if (dbIds.isEmpty()) {
                return@defer Single.just(output as R)
            }

            Single.fromCallable {
                val newResult = readFromDatabase(dbIds)
                saveInCache(newResult)
                output.addAll(newResult)
                output as R
            }.subscribeOn(queryScheduler)
        }
    }

    protected fun <T> queryList(mapper: (Cursor) -> T, sql: String, vararg args: String?): List<T> {
        return db.rawQuery(sql, args)?.use { cursor: Cursor ->
            ArrayList<T>(cursor.count).apply {
                if (cursor.moveToFirst()) {
                    do {
                        add(mapper(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } ?: emptyList<T>()
    }
}


fun createSQLiteStorageHelper(context: Context, dbName: String): SQLiteOpenHelper {
    return object : SQLiteOpenHelper(context, dbName, null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(Users.CREATE_SQL)
                db.execSQL(Groups.CREATE_SQL)
                db.execSQL(Rooms.CREATE_SQL)
                db.execSQL(Contacts.CREATE_SQL)
                db.execSQL(Messages.CREATE_SQL)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            for (v in oldVersion+1..newVersion) {
                logger.i { "Migrating database from v${v-1} to v$v" }
                if (v == 2) {
                    db.beginTransaction()
                    try {
                        db.execSQL(Users.MIGRATE_SQL_V1_V2)
                        db.setTransactionSuccessful()
                    }
                    finally {
                        db.endTransaction()
                    }
                }
                else if (v == 3) {
                    db.beginTransaction()
                    try {
                        db.execSQL(Messages.CREATE_SQL)
                        db.setTransactionSuccessful()
                    }
                    finally {
                        db.endTransaction()
                    }
                }
            }
        }
    }
}

class CacheMap<K, V>(private val maxItem : Int) : LinkedHashMap<K, V>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
        return size > maxItem
    }
}

private data class UserModel(override val id: String,
                             override val name: String,
                             override val priority: Int,
                             override val permissions: Set<Permission>,
                             override val avatar: String?,
                             override val phoneNumber: String?,
                             override val enterpriseId: String,
                             override val enterpriseName: String,
                             override val enterpriseExpireDate: Date?) : User

private fun User.toContentValues(contentValues: ContentValues = ContentValues(8)): ContentValues {
    contentValues.put(Users.ID, id)
    contentValues.put(Users.NAME, name)
    contentValues.put(Users.AVATAR, avatar)
    contentValues.put(Users.PRIORITY, priority)
    contentValues.put(Users.PERMISSIONS, permissions.toDbString())
    contentValues.put(Users.PHONE_NUMBER, phoneNumber)
    contentValues.put(Users.ENTERPRISE_ID, enterpriseId)
    contentValues.put(Users.ENTERPRISE_NAME, enterpriseName)
    contentValues.put(Users.ENTERPRISE_EXP_DATE, enterpriseExpireDate?.time)
    return contentValues
}

private object Users {
    const val TABLE_NAME = "users"

    const val ID = "person_id"
    const val NAME = "person_name"
    const val PERMISSIONS = "person_perms"
    const val PRIORITY = "person_level"
    const val AVATAR = "person_avatar"
    const val PHONE_NUMBER = "person_phone_number"
    const val ENTERPRISE_ID = "person_enterprise_id"
    const val ENTERPRISE_NAME = "person_enterprise_name"
    const val ENTERPRISE_EXP_DATE = "person_enterprise_exp_date"

    const val ALL = "$ID,$NAME,$AVATAR,$PRIORITY,$PERMISSIONS,$PHONE_NUMBER,$ENTERPRISE_ID,$ENTERPRISE_NAME,$ENTERPRISE_EXP_DATE"

    const val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($ID TEXT PRIMARY KEY NOT NULL, $NAME TEXT NOT NULL, $AVATAR TEXT, $PRIORITY INTEGER NOT NULL DEFAULT ${Constants.DEFAULT_USER_PRIORITY}, $PERMISSIONS TEXT NOT NULL, $PHONE_NUMBER TEXT, $ENTERPRISE_ID TEXT NOT NULL, $ENTERPRISE_NAME TEXT NOT NULL, $ENTERPRISE_EXP_DATE INTEGER)"

    const val MIGRATE_SQL_V1_V2 = "ALTER TABLE $TABLE_NAME ADD COLUMN $ENTERPRISE_EXP_DATE INTEGER"

    val MAPPER: (Cursor) -> User = { cursor ->
        UserModel(
                id = cursor.getString(0),
                name = cursor.getString(1),
                avatar = cursor.getString(2),
                priority = cursor.getInt(3),
                permissions = cursor.getString(4).toPermissionSet(),
                phoneNumber = cursor.getString(5),
                enterpriseId = cursor.getString(6),
                enterpriseName = cursor.getString(7),
                enterpriseExpireDate = cursor.getLong(8).let{ if (it <= 0) null else Date(it) }
        )
    }
}

private data class GroupModel(override val id: String,
                              override val name: String,
                              override val description: String?,
                              override val avatar: String?,
                              override val memberIds: Collection<String>) : Group

private fun Group.toContentValues(contentValues: ContentValues = ContentValues(5)): ContentValues {
    contentValues.put(Groups.ID, id)
    contentValues.put(Groups.NAME, name)
    contentValues.put(Groups.DESCRIPTION, description)
    contentValues.put(Groups.AVATAR, avatar)
    contentValues.put(Groups.MEMBER_IDS, memberIds.joinToString(","))
    return contentValues
}

private object Groups {
    const val TABLE_NAME = "groups"

    const val ID = "group_id"
    const val NAME = "group_name"
    const val DESCRIPTION = "group_desc"
    const val AVATAR = "group_avatar"
    const val MEMBER_IDS = "group_member_ids"

    const val ALL = "$ID,$NAME,$DESCRIPTION,$AVATAR,$MEMBER_IDS"

    val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($ID INTEGER PRIMARY KEY NOT NULL,$NAME TEXT NOT NULL,$DESCRIPTION TEXT,$AVATAR TEXT,$MEMBER_IDS TEXT)"

    val MAPPER: (Cursor) -> Group = { cursor ->
        GroupModel(
                id = cursor.getString(0),
                name = cursor.getString(1),
                description = cursor.getString(2),
                avatar = cursor.getString(3),
                memberIds = cursor.getString(4).lazySplit(',')
        )
    }
}

private data class RoomModelImpl(override val id: String,
                                 override val name: String,
                                 override val description: String?,
                                 override val ownerId: String,
                                 override val lastSpeakMemberId: String?,
                                 override val lastSpeakTime: Date?,
                                 override val lastActiveTime: Date,
                                 override val extraMemberIds: Collection<String>,
                                 override val associatedGroupIds: Collection<String>) : RoomModel

private fun Room.toContentValues(contentValues: ContentValues = ContentValues(6)): ContentValues {
    contentValues.put(Rooms.ID, id)
//    contentValues.put(Rooms.NAME, name)
    contentValues.put(Rooms.DESC, description)
    contentValues.put(Rooms.OWNER_ID, ownerId)
    contentValues.put(Rooms.EXTRA_MEMBER_IDS, extraMemberIds.joinToString(separator = ","))
    contentValues.put(Rooms.ASSOCIATED_GROUP_IDS, associatedGroupIds.joinToString(separator = ","))
    return contentValues
}

private object Rooms {
    const val TABLE_NAME = "rooms"

    const val ID = "room_id"
    const val NAME = "room_name"
    const val DESC = "room_desc"
    const val OWNER_ID = "room_owner_id"
    const val LAST_SPEAK_MEMBER_ID = "room_last_speak_member_id"
    const val LAST_SPEAK_TIME = "room_last_speak_time"
    const val LAST_ACTIVE_TIME = "room_last_active_time"
    const val EXTRA_MEMBER_IDS = "room_extra_member_ids"
    const val ASSOCIATED_GROUP_IDS = "room_associated_group_ids"

    const val ALL = "$ID,$NAME,$DESC,$OWNER_ID,$LAST_SPEAK_MEMBER_ID,$LAST_SPEAK_TIME,$LAST_ACTIVE_TIME,$EXTRA_MEMBER_IDS,$ASSOCIATED_GROUP_IDS"

    val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($ID TEXT PRIMARY KEY,$NAME TEXT,$DESC TEXT,$OWNER_ID TEXT NOT NULL,$LAST_SPEAK_MEMBER_ID TEXT,$LAST_SPEAK_TIME INTEGER, $LAST_ACTIVE_TIME INTEGER NOT NULL,$EXTRA_MEMBER_IDS TEXT,$ASSOCIATED_GROUP_IDS TEXT)"

    val MAPPER: (Cursor) -> RoomModel = { cursor ->
        RoomModelImpl(
                id = cursor.getString(0),
                name = cursor.getString(1) ?: "",
                description = cursor.getString(2),
                ownerId = cursor.getString(3),
                lastSpeakMemberId = cursor.getString(4),
                lastSpeakTime = cursor.getLong(5).let { if (it <= 0L) null else Date(it) },
                lastActiveTime = Date(cursor.getLong(6)),
                extraMemberIds = cursor.getString(7).lazySplit(','),
                associatedGroupIds = cursor.getString(8).lazySplit(',')
        )
    }
}

private object Contacts {
    const val TABLE_NAME = "contacts"

    const val GROUP_ID = "contact_group_id"
    const val USER_ID = "contact_person_id"

    const val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($GROUP_ID TEXT,$USER_ID TEXT,UNIQUE ($GROUP_ID, $USER_ID))"
}


private fun Set<Permission>.toDbString(): String {
    return joinToString(separator = ",", transform = { it.toString() })
}

private fun String?.toPermissionSet(): Set<Permission> {
    if (this == null) {
        return emptySet()
    }

    val set = EnumSet.noneOf(Permission::class.java)
    split(',').forEach {
        try {
            set.add(Permission.valueOf(it))
        } catch(e: IllegalArgumentException) {
            // ignore
        }
    }

    return set
}
