package com.xianzhitech.model

import android.content.ContentValues
import android.database.Cursor
import com.xianzhitech.ext.getIntValue
import com.xianzhitech.ext.getStringValue
import com.xianzhitech.ext.optStringValue
import org.json.JSONObject
import rx.functions.Func1

class Person() : Model, ContactItem {
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

        public val MAPPER = Func1<Cursor, Person> { Person().from(it) }
    }

    var id: String = ""
        private set

    override var name = ""
        private set

    override var avatar: String? = ""
        private set

    override var tintColor: Int = 0
        private set

    var privilege: Int = 0
        private set

    constructor(id: String, name: String, privilege: Int) : this() {
        this.id = id
        this.name = name
        this.privilege = privilege
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
        values.put(COL_PRIV, privilege)
    }

    override fun from(cursor: Cursor): Person {
        id = cursor.getStringValue(COL_ID)
        avatar = cursor.optStringValue(COL_AVATAR)
        name = cursor.getStringValue(COL_NAME)
        privilege = cursor.getIntValue(COL_PRIV)
        return this
    }

    fun readFrom(obj: JSONObject) = {
        id = obj.getString("idNumber")
        name = obj.getString("name")
        avatar = obj.optString("avatar")
        privilege = obj.optJSONObject("privileges").toPrivilege()
        this
    }

}