package com.xianzhitech.model

import android.database.Cursor
import com.xianzhitech.ext.optStringValue
import rx.functions.Func1

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