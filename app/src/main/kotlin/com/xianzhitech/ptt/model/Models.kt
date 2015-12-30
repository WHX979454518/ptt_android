package com.xianzhitech.ptt.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.drawable.Drawable
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.getIntValue
import com.xianzhitech.ptt.ext.getStringValue
import com.xianzhitech.ptt.ext.optStringValue
import com.xianzhitech.ptt.ui.widget.TextDrawable
import rx.functions.Func1
import java.io.Serializable
import java.util.*
import kotlin.text.isNullOrEmpty
import kotlin.text.substring

/**
 *
 * 所有的数据库相关模型
 *
 * Created by fanchao on 17/12/15.
 */

interface Model {
    fun toValues(values: ContentValues)
    fun from(cursor: Cursor): Model
}

interface ContactItem {
    fun getTintColor(context: Context): Int
    fun getIcon(context: Context): Drawable

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
                "$COL_PERSON_ID TEXT REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID}) UNIQUE ON CONFLICT IGNORE" +
                ")"

        public @JvmField val MAPPER = Func1<Cursor, ContactItem> { cursor ->
            if (cursor?.optStringValue(COL_GROUP_ID).isNullOrEmpty().not()) {
                Group.MAPPER.call(cursor)
            } else if (cursor?.optStringValue(COL_PERSON_ID).isNullOrEmpty().not()) {
                Person.MAPPER.call(cursor)
            } else {
                throw IllegalArgumentException("Cursor $cursor is not a valid contact")
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
class Conversation() : Model {

    companion object {
        public const val TABLE_NAME = "conversations"

        public const val COL_ID = "conv_id"
        public const val COL_NAME = "conv_name"
        public const val COL_DESC = "conv_desc"
        public const val COL_OWNER_ID = "conv_owner_id"
        public const val COL_IMPORTANT = "conv_important"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ID TEXT PRIMARY KEY," +
                "$COL_NAME TEXT," +
                "$COL_DESC TEXT," +
                "$COL_OWNER_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID})," +
                "$COL_IMPORTANT INTEGER NOT NULL" +
                ")"

        public @JvmStatic val MAPPER = Func1<Cursor, Conversation> { Conversation().from(it) }
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

        return other is Conversation && other.id == id
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: ContentValues) {
        values.put(COL_ID, id)
        values.put(COL_NAME, name)
        values.put(COL_DESC, description)
        values.put(COL_OWNER_ID, ownerId)
        values.put(COL_IMPORTANT, important)
    }

    override fun from(cursor: Cursor): Conversation {
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

        public @JvmField val MAPPER = Func1<Cursor, Group> { Group().from(it) }
    }

    var id: String = ""
    var description: String? = null
    override var name = ""
    override var avatar: String? = null

    constructor(id: String, name: String) : this() {
        this.id = id
        this.name = name
    }

    override fun getTintColor(context: Context) = context.resources.getIntArray(R.array.account_colors).let {
        it[hashCode() % it.size]
    }

    override fun getIcon(context: Context) = TextDrawable(name.substring(0, 1), getTintColor(context))

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return if (other is Group) other.id == id else false
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: ContentValues) {
        values.put(COL_ID, id)
        values.put(COL_DESCRIPTION, description)
        values.put(COL_NAME, name)
    }

    override fun from(cursor: Cursor): Group {
        id = cursor.getStringValue(COL_ID)
        description = cursor.optStringValue(COL_DESCRIPTION)
        name = cursor.getStringValue(COL_NAME)
        return this
    }
}

class Person() : Model, ContactItem, Serializable {
    companion object {
        public const val TABLE_NAME = "persons"

        public const val COL_ID = "person_id"
        public const val COL_NAME = "person_name"
        public const val COL_PRIV = "person_priv"
        public const val COL_AVATAR = "person_avatar"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME ($COL_ID TEXT PRIMARY KEY NOT NULL, $COL_NAME TEXT NOT NULL, $COL_AVATAR TEXT, $COL_PRIV INTEGER NOT NULL)"

        public @JvmField val MAPPER = Func1<Cursor, Person> { Person().from(it) }
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

    override fun getTintColor(context: Context) = context.resources.getIntArray(R.array.account_colors).let {
        it[hashCode() % it.size]
    }

    override fun getIcon(context: Context) = TextDrawable(name.substring(0, 1), getTintColor(context))

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true;
        }

        return other is Person && other.id == id
    }

    override fun hashCode() = id.hashCode()

    override fun toValues(values: ContentValues) {
        values.put(COL_ID, id)
        values.put(COL_AVATAR, avatar)
        values.put(COL_NAME, name)
        values.put(COL_PRIV, privileges.toDatabaseString())
    }

    override fun from(cursor: Cursor): Person {
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
                "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID}) ON DELETE CASCADE, " +
                "UNIQUE ($COL_GROUP_ID,$COL_PERSON_ID) ON CONFLICT REPLACE" +
                ")"
    }
}

class ConversationMembers {
    companion object {
        public const val TABLE_NAME = "conversation_members"

        public const val COL_CONVERSATION_ID = "cm_conv_id"
        public const val COL_PERSON_ID = "cm_person_id"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_CONVERSATION_ID TEXT NOT NULL REFERENCES ${Conversation.TABLE_NAME}(${Conversation.COL_ID})," +
                "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID})" +
                ")"
    }
}