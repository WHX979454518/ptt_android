package com.xianzhitech.model

import android.content.ContentValues
import android.database.Cursor
import com.xianzhitech.ext.getStringValue
import com.xianzhitech.ext.optStringValue
import rx.functions.Func1

/**
 * Created by fanchao on 17/12/15.
 */
class Group() : Model, ContactItem {
    companion object {
        public const val TABLE_NAME = "groups"

        public const val COL_ID = "group_id"
        public const val COL_NAME = "group_name"
        public const val COL_DESCRIPTION = "group_desc"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ID INTEGER PRIMARY KEY NOT NULL," +
                "$COL_NAME TEXT NOT NULL," +
                "$COL_DESCRIPTION TEXT" +
                ")"

        public val MAPPER = Func1<Cursor, Group> { Group().from(it) }
    }

    var id: String = ""
        private set
    var description: String? = null
        private set
    override val tintColor: Int = 0
    override var name = ""
        private set
    override var avatar: String? = null
        private set

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

    override fun toValues(values: ContentValues) {
        values.put(COL_ID, id)
        values.put(COL_DESCRIPTION, description)
    }

    override fun from(cursor: Cursor): Group {
        id = cursor.getStringValue(COL_ID)
        description = cursor.optStringValue(COL_DESCRIPTION)
        name = cursor.getStringValue(COL_NAME)
        return this
    }
}