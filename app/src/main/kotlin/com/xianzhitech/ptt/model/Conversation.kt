package com.xianzhitech.ptt.model

import android.content.ContentValues
import android.database.Cursor
import com.xianzhitech.ptt.ext.getIntValue
import com.xianzhitech.ptt.ext.getStringValue
import com.xianzhitech.ptt.ext.optStringValue
import rx.functions.Func1

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