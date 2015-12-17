package com.xianzhitech.model

import android.content.ContentValues
import android.database.Cursor
import com.xianzhitech.ext.getIntValue
import com.xianzhitech.ext.getStringValue
import com.xianzhitech.ext.optStringValue

/**
 * Created by fanchao on 17/12/15.
 */
class Conversation() : Model {

    companion object {
        public const val TABLE_NAME = "conversations"

        public const val COL_ID = "conv_id"
        public const val COL_NAME = "conv_name"
        public const val COL_DESC = "conv_desc"
        public const val COL_OWNER_ID = "conv_owner_id"
        public const val COL_PRIORITY = "conv_priority"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_ID TEXT PRIMARY KEY," +
                "$COL_NAME TEXT," +
                "$COL_DESC TEXT," +
                "$COL_OWNER_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID}),$COL_PRIORITY INTEGER NOT NULL" +
                ")"
    }

    var id: String = ""
        private set
    var name: String = ""
        private set
    var description: String? = null
        private set
    var ownerId: String = ""
        private set
    var priority: Int = 0
        private set

    constructor(id: String, name: String, description: String?, ownerId: String, priority: Int) : this() {
        this.id = id
        this.name = name
        this.description = description
        this.ownerId = ownerId
        this.priority = priority
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
        values.put(COL_PRIORITY, priority)
    }

    override fun from(cursor: Cursor): Conversation {
        id = cursor.getStringValue(COL_ID)
        name = cursor.getStringValue(COL_NAME)
        description = cursor.optStringValue(COL_DESC)
        ownerId = cursor.getStringValue(COL_OWNER_ID)
        priority = cursor.getIntValue(COL_PRIORITY)
        return this
    }
}