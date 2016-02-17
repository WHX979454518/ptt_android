package com.xianzhitech.ptt.model

import com.xianzhitech.ptt.db.ResultSet
import com.xianzhitech.ptt.ext.getIntValue
import com.xianzhitech.ptt.ext.getStringValue
import com.xianzhitech.ptt.ext.optStringValue
import rx.functions.Func1
import java.io.Serializable
import java.util.*

/**
 *
 * 所有的数据库相关模型
 *
 * Created by fanchao on 17/12/15.
 */

interface Model {
    fun toValues(values: MutableMap<String, Any?>)
    fun from(cursor: ResultSet): Model
}

interface ContactItem {
    val name: CharSequence
    val avatar: String?
}

class Contacts {
    companion object {
        public const val TABLE_NAME = "contacts";

        public const val COL_GROUP_ID = "contact_group_id"
        public const val COL_PERSON_ID = "contact_person_id"
        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_GROUP_ID TEXT REFERENCES ${Group.TABLE_NAME}(${Group.COL_ID}) UNIQUE ON CONFLICT IGNORE," +
                "$COL_PERSON_ID TEXT REFERENCES ${User.TABLE_NAME}(${User.COL_ID}) UNIQUE ON CONFLICT IGNORE" +
                ")"

        public @JvmField val MAPPER = Func1<ResultSet, ContactItem> { cursor ->
            if (cursor?.optStringValue(COL_GROUP_ID).isNullOrEmpty().not()) {
                Group.MAPPER.call(cursor)
            } else if (cursor?.optStringValue(COL_PERSON_ID).isNullOrEmpty().not()) {
                User.MAPPER.call(cursor)
            } else {
                throw IllegalArgumentException("ResultSet $cursor is not a valid contact")
            }
        }
    }
}

/**
 *
 * 会话模型
 *
 * Created by fanchao on 17/12/15.
 */
class Room() : Model {

    companion object {
        public const val TABLE_NAME = "rooms"

        public const val COL_ID = "room_id"
        public const val COL_NAME = "room_name"
        public const val COL_DESC = "room_desc"
        public const val COL_OWNER_ID = "room_owner_id"
        public const val COL_IMPORTANT = "room_important"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ID TEXT PRIMARY KEY," +
                "$COL_NAME TEXT," +
                "$COL_DESC TEXT," +
                "$COL_OWNER_ID TEXT NOT NULL REFERENCES ${User.TABLE_NAME}(${User.COL_ID})," +
                "$COL_IMPORTANT INTEGER NOT NULL" +
                ")"

        public @JvmStatic val MAPPER = Func1<ResultSet, Room> { Room().from(it) }
    }

    var id: String = ""
    var name: String = ""
    var description: String? = null
    var ownerId: String = ""
    var important: Boolean = false

    constructor(id: String, name: String, description: String?, ownerId: String, isImportant: Boolean) : this() {
        this.id = id
        this.name = name
        this.description = description
        this.ownerId = ownerId
        this.important = isImportant
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        return other is Room && other.id == id
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: MutableMap<String, Any?>) {
        values.put(COL_ID, id)
        values.put(COL_NAME, name)
        values.put(COL_DESC, description)
        values.put(COL_OWNER_ID, ownerId)
        values.put(COL_IMPORTANT, important)
    }

    override fun from(cursor: ResultSet): Room {
        id = cursor.getStringValue(COL_ID)
        name = cursor.getStringValue(COL_NAME)
        description = cursor.optStringValue(COL_DESC)
        ownerId = cursor.getStringValue(COL_OWNER_ID)
        important = cursor.getIntValue(COL_IMPORTANT) != 0
        return this
    }
}

/**
 * 通讯录组模型
 *
 * Created by fanchao on 17/12/15.
 */
class Group() : Model, ContactItem {
    companion object {
        public const val TABLE_NAME = "groups"

        public const val COL_ID = "group_id"
        public const val COL_NAME = "group_name"
        public const val COL_DESCRIPTION = "group_desc"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME ($COL_ID INTEGER PRIMARY KEY NOT NULL,$COL_NAME TEXT NOT NULL,$COL_DESCRIPTION TEXT)"

        public @JvmField val MAPPER = Func1<ResultSet, Group> { Group().from(it) }
    }

    var id: String = ""
    var description: String? = null
    override var name = ""
    override var avatar: String? = null

    constructor(id: String, name: String) : this() {
        this.id = id
        this.name = name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return if (other is Group) other.id == id else false
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: MutableMap<String, Any?>) {
        values.put(COL_ID, id)
        values.put(COL_DESCRIPTION, description)
        values.put(COL_NAME, name)
    }

    override fun from(cursor: ResultSet): Group {
        id = cursor.getStringValue(COL_ID)
        description = cursor.optStringValue(COL_DESCRIPTION)
        name = cursor.getStringValue(COL_NAME)
        return this
    }
}

class User() : Model, ContactItem, Serializable {
    companion object {
        public const val TABLE_NAME = "users"

        public const val COL_ID = "person_id"
        public const val COL_NAME = "person_name"
        public const val COL_PRIV = "person_priv"
        public const val COL_AVATAR = "person_avatar"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME ($COL_ID TEXT PRIMARY KEY NOT NULL, $COL_NAME TEXT NOT NULL, $COL_AVATAR TEXT, $COL_PRIV INTEGER NOT NULL)"

        public @JvmField val MAPPER = Func1<ResultSet, User> {
            User().from(it)
        }
    }

    var id: String = ""
    override var name = ""
    override var avatar: String? = ""
    var privileges = EnumSet.noneOf(Privilege::class.java)

    constructor(id: String, name: String, privileges: EnumSet<Privilege>) : this() {
        this.id = id
        this.name = name
        this.privileges = privileges
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true;
        }

        return other is User && other.id == id
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: MutableMap<String, Any?>) {
        values.put(COL_ID, id)
        values.put(COL_AVATAR, avatar)
        values.put(COL_NAME, name)
        values.put(COL_PRIV, privileges.toDatabaseString())
    }

    override fun from(cursor: ResultSet): User {
        id = cursor.getStringValue(COL_ID)
        avatar = cursor.optStringValue(COL_AVATAR)
        name = cursor.getStringValue(COL_NAME)
        privileges = cursor.getStringValue(COL_PRIV).toPrivileges()
        return this
    }
}

class GroupMembers {
    companion object {
        public const val TABLE_NAME = "group_members"

        public const val COL_GROUP_ID = "gm_group_id"
        public const val COL_PERSON_ID = "gm_person_id"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME(" +
                "$COL_GROUP_ID TEXT NOT NULL REFERENCES ${Group.TABLE_NAME}(${Group.COL_ID}) ON DELETE CASCADE," +
                "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${User.TABLE_NAME}(${User.COL_ID}) ON DELETE CASCADE, " +
                "UNIQUE ($COL_GROUP_ID,$COL_PERSON_ID) ON CONFLICT REPLACE" +
                ")"
    }
}

class RoomMembers {
    companion object {
        public const val TABLE_NAME = "room_members"

        public const val COL_ROOM_ID = "rm_room_id"
        public const val COL_USER_ID = "rm_user_id"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ROOM_ID TEXT NOT NULL REFERENCES ${Room.TABLE_NAME}(${Room.COL_ID})," +
                "$COL_USER_ID TEXT NOT NULL REFERENCES ${User.TABLE_NAME}(${User.COL_ID})" +
                ")"
    }
}