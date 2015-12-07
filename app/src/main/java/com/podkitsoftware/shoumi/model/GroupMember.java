package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.podkitsoftware.shoumi.util.CursorUtil;


public class GroupMember implements Model {

    public static final String COL_ID = "id";
    public static final String COL_GROUP_ID = "group_id";
    public static final String COL_PERSON_ID = "person_id";
    public static final String TABLE_NAME = "group_members";

    private Long id;
    private long groupId;
    private long personId;

    public GroupMember() {
    }

    public GroupMember(long groupId, long personId) {
        this.groupId = groupId;
        this.personId = personId;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_GROUP_ID, groupId);
        values.put(COL_PERSON_ID, personId);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getOptionalLong(cursor, COL_ID);
        groupId = CursorUtil.getLong(cursor, COL_GROUP_ID);
        personId = CursorUtil.getLong(cursor, COL_PERSON_ID);
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + "(" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    COL_GROUP_ID + " INTEGER NOT NULL REFERENCES "
                                + Group.TABLE_NAME + "(" + Group.COL_ID + ") ON DELETE CASCADE, " +
                    COL_PERSON_ID + " INTEGER NOT NULL REFERENCES "
                                + Person.TABLE_NAME + "(" + Person.COL_ID + ") ON DELETE CASCADE, " +
                "UNIQUE (" + COL_GROUP_ID + "," + COL_PERSON_ID + ") ON CONFLICT REPLACE)";
    }
}
