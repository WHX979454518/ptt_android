package com.xianzhitech.ptt.model

import android.content.ContentValues
import android.database.Cursor
import com.xianzhitech.ptt.ext.getStringValue
import com.xianzhitech.ptt.ext.optStringValue
import rx.functions.Func1
import java.io.Serializable
import java.util.*

class Person() : Model, ContactItem, Serializable {
    companion object {
        public const val TABLE_NAME = "persons"

        public const val COL_ID = "person_id"
        public const val COL_NAME = "person_name"
        public const val COL_PRIV = "person_priv"
        public const val COL_AVATAR = "person_avatar"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ID TEXT PRIMARY KEY NOT NULL, $COL_NAME TEXT NOT NULL, " +
                "$COL_AVATAR TEXT, " +
                "$COL_PRIV INTEGER NOT NULL" +
                ")"

        public @JvmField val MAPPER = Func1<Cursor, Person> { Person().from(it) }
    }

    var id: String = ""
    override var name = ""
    override var avatar: String? = ""
    override var tintColor: Int = 0
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