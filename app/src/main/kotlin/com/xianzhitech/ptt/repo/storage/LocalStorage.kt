package com.xianzhitech.ptt.repo.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.lazySplit
import com.xianzhitech.ptt.ext.logtagd
import com.xianzhitech.ptt.ext.toSqlSet
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.repo.ExtraRoomInfo
import java.util.*


class UserSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage(db), UserStorage {
    override fun getUsers(ids: Iterable<String>, out: MutableList<User>): List<User> {
        return queryList(Users.MAPPER, out, "SELECT ${Users.ALL} FROM ${Users.TABLE_NAME} WHERE ${Users.ID} IN ${ids.toSqlSet()}")
    }

    override fun saveUsers(users: Iterable<User>)  {
        return executeInTransaction {
            val contentValues = ContentValues()
            users.forEach {
                db.insertWithOnConflict(Users.TABLE_NAME, null, it.toContentValues(contentValues), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }
}

class GroupSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage(db), GroupStorage {
    override fun getGroups(groupIds: Iterable<String>, out: MutableList<Group>): List<Group> {
        return queryList(Groups.MAPPER, out, "SELECT ${Groups.ALL} FROM ${Groups.TABLE_NAME} WHERE ${Groups.ID} IN ${groupIds.toSqlSet()}")
    }

    override fun saveGroups(groups: Iterable<Group>)  {
        return executeInTransaction {
            val contentValues = ContentValues()
            groups.forEach {
                db.insertWithOnConflict(Groups.TABLE_NAME, null, it.toContentValues(contentValues), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }
}

class RoomSQLiteStorage(db: SQLiteOpenHelper) : BaseSQLiteStorage(db), RoomStorage {
    override fun getAllRooms(): List<Room> {
        return queryList(Rooms.MAPPER, arrayListOf(), "SELECT ${Rooms.ALL} FROM ${Rooms.TABLE_NAME}")
    }

    override fun getRooms(roomIds: Iterable<String>): List<Room> {
        return queryList(Rooms.MAPPER, arrayListOf(), "SELECT ${Rooms.ALL} FROM ${Rooms.TABLE_NAME} WHERE ${Rooms.ID} IN ${roomIds.toSqlSet()}")
    }

    override fun updateLastRoomActiveUser(roomId: String, activeTime: Date, activeMemberId: String)  {
        return executeInTransaction {
            val contentValue = ContentValues(2)
            contentValue.put(Rooms.LAST_ACTIVE_TIME, activeTime.time)
            contentValue.put(Rooms.LAST_ACTIVE_MEMBER_ID, activeMemberId)
            db.update(Rooms.TABLE_NAME, contentValue, "${Rooms.ID} = ?", arrayOf(roomId))
        }
    }

    override fun saveRooms(rooms: Iterable<Room>)  {
        return executeInTransaction {
            val contentValues = ContentValues()
            rooms.forEach {
                if (db.insert(Rooms.TABLE_NAME, null, it.toContentValues(contentValues)) == -1L) {
                    db.update(Rooms.TABLE_NAME, contentValues, "${Rooms.ID} = ?", arrayOf(it.id))
                }
            }
        }
    }

    override fun clearRooms()  {
        return executeInTransaction {
            db.delete(Rooms.TABLE_NAME, "1", arrayOf())
        }
    }
}

class ContactSQLiteStorage(db: SQLiteOpenHelper,
                           private val userStorage: UserStorage,
                           private val groupStorage: GroupStorage) : BaseSQLiteStorage(db), ContactStorage {

    override fun getContactItems(): List<Model> {
        val result = ArrayList<Model>()

        // Query groups
        db.rawQuery("SELECT ${Contacts.GROUP_ID} FROM ${Contacts.TABLE_NAME}", arrayOf())?.use { cursor ->
            if (cursor.moveToFirst()) {
                groupStorage.getGroups(cursor.mapToIterable { it.getString(0) }, result as MutableList<Group>)
            }
        }

        // Query users
        db.rawQuery("SELECT ${Contacts.USER_ID} FROM ${Contacts.TABLE_NAME}", arrayOf())?.use { cursor ->
            if (cursor.moveToFirst()) {
                userStorage.getUsers(cursor.mapToIterable { it.getString(0) }, result as MutableList<User>)
            }
        }

        return result
    }

    override fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>)  {
        return executeInTransaction {
            userStorage.saveUsers(users)
            groupStorage.saveGroups(groups)

            val localDb = db

            localDb.delete(Contacts.TABLE_NAME, "1", arrayOf())
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
        }
    }
}


open class BaseSQLiteStorage(dbOpenHelper : SQLiteOpenHelper) {

    protected val db : SQLiteDatabase by lazy { dbOpenHelper.writableDatabase }

    protected inline fun executeInTransaction(func: () -> Unit)   {
        db.beginTransaction()
        try {
            func()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    protected fun <T> queryList(mapper : (Cursor) -> T, out: MutableList<T>, sql: String, vararg args : String?) : List<T> {
        val startTime = System.currentTimeMillis()
        return db.rawQuery(sql, args)?.use { cursor : Cursor ->
            out.apply {
                out.ensureMoreCapacity(cursor.count)

                if (cursor.moveToFirst()) {
                    do {
                        add(mapper(cursor))
                    } while (cursor.moveToNext())
                }

                logtagd("LocalStorage", "Query and map costs %dms: ${sql.substring(0, Math.min(sql.length, 200))}", System.currentTimeMillis() - startTime)
            }
        } ?: emptyList<T>()
    }
}


fun createSQLiteStorageHelper(context: Context, dbName : String) : SQLiteOpenHelper {
    return object : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(Users.CREATE_SQL)
                db.execSQL(Groups.CREATE_SQL)
                db.execSQL(Rooms.CREATE_SQL)
                db.execSQL(Contacts.CREATE_SQL)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            // Nothing to do now
        }
    }
}


private data class UserModel(override val id: String,
                             override val name: String,
                             override val priority: Int,
                             override val permissions: Set<Permission>,
                             override val avatar: String?) : User

private fun User.toContentValues(contentValues: ContentValues = ContentValues(5)) : ContentValues {
    contentValues.put(Users.ID, id)
    contentValues.put(Users.NAME, name)
    contentValues.put(Users.AVATAR, avatar)
    contentValues.put(Users.PRIORITY, priority)
    contentValues.put(Users.PERMISSIONS, permissions.toDbString())
    return contentValues
}

private object Users {
    const val TABLE_NAME = "users"

    const val ID = "person_id"
    const val NAME = "person_name"
    const val PERMISSIONS = "person_perms"
    const val PRIORITY = "person_level"
    const val AVATAR = "person_avatar"

    const val ALL = "$ID,$NAME,$PERMISSIONS,$PRIORITY,$AVATAR"

    const val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($ID TEXT PRIMARY KEY NOT NULL, $NAME TEXT NOT NULL, $AVATAR TEXT, $PRIORITY INTEGER NOT NULL DEFAULT ${Constants.DEFAULT_USER_PRIORITY}, $PERMISSIONS TEXT NOT NULL)"

    val MAPPER : (Cursor) -> User = { cursor ->
        UserModel(
                id = cursor.getString(0),
                name = cursor.getString(1),
                avatar = cursor.getString(2),
                priority = cursor.getInt(3),
                permissions = cursor.getString(4).toPermissionSet()
        )
    }
}

private data class GroupModel(override val id: String,
                              override val name: String,
                              override val description: String?,
                              override val avatar: String?,
                              override val memberIds: Iterable<String>) : Group

private fun Group.toContentValues(contentValues: ContentValues = ContentValues(5)) : ContentValues {
    contentValues.put(Groups.ID, id)
    contentValues.put(Groups.NAME, name)
    contentValues.put(Groups.DESCRIPTION, description)
    contentValues.put(Groups.AVATAR, avatar)
    contentValues.put(Groups.MEMBER_IDS, memberIds.joinToString(","))
    return contentValues
}

private object Groups  {
    const val TABLE_NAME = "groups"

    const val ID = "group_id"
    const val NAME = "group_name"
    const val DESCRIPTION = "group_desc"
    const val AVATAR = "group_avatar"
    const val MEMBER_IDS = "group_member_ids"

    const val ALL = "$ID,$NAME,$DESCRIPTION,$AVATAR,$MEMBER_IDS"

    val CREATE_SQL = "CREATE TABLE ${TABLE_NAME} (${ID} INTEGER PRIMARY KEY NOT NULL,${NAME} TEXT NOT NULL,${DESCRIPTION} TEXT,${AVATAR} TEXT,${MEMBER_IDS} TEXT)"

    val MAPPER : (Cursor) -> Group = { cursor ->
        GroupModel(
                id = cursor.getString(0),
                name = cursor.getString(1),
                description = cursor.getString(2),
                avatar = cursor.getString(3),
                memberIds = cursor.getString(4).lazySplit(',')
        )
    }
}

private data class RoomModel(override val id: String,
                             override val name: String,
                             override val description: String?,
                             override val ownerId: String,
                             override val lastActiveMemberId: String?,
                             override val lastActiveTime: Date?,
                             override val extraMemberIds: Iterable<String>,
                             override val associatedGroupIds: Iterable<String>) : Room, ExtraRoomInfo

private fun Room.toContentValues(contentValues: ContentValues = ContentValues(6)) : ContentValues {
    contentValues.put(Rooms.ID, id)
    contentValues.put(Rooms.NAME, name)
    contentValues.put(Rooms.DESC, description)
    contentValues.put(Rooms.OWNER_ID, ownerId)
    contentValues.put(Rooms.EXTRA_MEMBER_IDS, extraMemberIds.joinToString(separator = ","))
    contentValues.put(Rooms.ASSOCIATED_GROUP_IDS, associatedGroupIds.joinToString(separator = ","))
    return contentValues
}

private object Rooms  {
    const val TABLE_NAME = "rooms"

    const val ID = "room_id"
    const val NAME = "room_name"
    const val DESC = "room_desc"
    const val OWNER_ID = "room_owner_id"
    const val LAST_ACTIVE_MEMBER_ID = "room_last_active_user_id"
    const val LAST_ACTIVE_TIME = "room_last_active_time"
    const val EXTRA_MEMBER_IDS = "room_extra_member_ids"
    const val ASSOCIATED_GROUP_IDS = "room_associated_group_ids"

    const val ALL = "$ID,$NAME,$DESC,$OWNER_ID,$LAST_ACTIVE_MEMBER_ID,$LAST_ACTIVE_TIME,$EXTRA_MEMBER_IDS,$ASSOCIATED_GROUP_IDS"

    val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($ID TEXT PRIMARY KEY,$NAME TEXT,$DESC TEXT,$OWNER_ID TEXT NOT NULL,$LAST_ACTIVE_MEMBER_ID TEXT,$LAST_ACTIVE_TIME INTEGER,$EXTRA_MEMBER_IDS TEXT,$ASSOCIATED_GROUP_IDS TEXT)"

    val MAPPER : (Cursor) -> Room = { cursor ->
        RoomModel(
                id = cursor.getString(0),
                name = cursor.getString(1),
                description = cursor.getString(2),
                ownerId = cursor.getString(3),
                lastActiveMemberId = cursor.getString(4),
                lastActiveTime = cursor.getLong(5).let { if (it <= 0L) null else Date(it) },
                extraMemberIds = cursor.getString(6).lazySplit(','),
                associatedGroupIds = cursor.getString(7).lazySplit(',')
        )
    }
}

private object Contacts  {
    const val TABLE_NAME = "contacts";

    const val GROUP_ID = "contact_group_id"
    const val USER_ID = "contact_person_id"

    const val CREATE_SQL = "CREATE TABLE $TABLE_NAME ($GROUP_ID TEXT,$USER_ID TEXT,UNIQUE ($GROUP_ID, $USER_ID))"
}


private fun Set<Permission>.toDbString() : String {
    return joinToString(separator = ",", transform = { it.toString() })
}

private fun String?.toPermissionSet() : Set<Permission> {
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

private fun <T> MutableList<T>.ensureMoreCapacity(cap : Int) {
    if (this is ArrayList) {
        ensureCapacity(size + cap)
    }
}

private fun <T> Cursor.mapToIterable(mapper : (Cursor) -> T) : Iterable<T> {
    return object : Iterable<T> {
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                override fun hasNext(): Boolean {
                    return this@mapToIterable.isAfterLast.not()
                }

                override fun next(): T {
                    moveToNext()
                    return mapper(this@mapToIterable)
                }
            }
        }
    }
}
