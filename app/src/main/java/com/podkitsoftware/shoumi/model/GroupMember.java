package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.podkitsoftware.shoumi.util.CursorUtil;


public class GroupMember implements Model {

    public static final String TABLE_NAME = "group_members";

    public static final String COL_ID = "gm_id";
    public static final String COL_GROUP_ID = "gm_group_id";
    public static final String COL_PERSON_ID = "gm_person_id";

    private Long id;
    private String groupId;
    private String personId;

    public GroupMember() {
    }

    public GroupMember(String groupId, String personId) {
        this.groupId = groupId;
        this.personId = personId;
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
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
        groupId = CursorUtil.getString(cursor, COL_GROUP_ID);
        personId = CursorUtil.getString(cursor, COL_PERSON_ID);
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + "(" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    COL_GROUP_ID + " TEXT NOT NULL REFERENCES "
                                + Group.TABLE_NAME + "(" + Group.COL_ID + ") ON DELETE CASCADE, " +
                    COL_PERSON_ID + " TEXT NOT NULL REFERENCES "
                                + Person.TABLE_NAME + "(" + Person.COL_ID + ") ON DELETE CASCADE, " +
                "UNIQUE (" + COL_GROUP_ID + "," + COL_PERSON_ID + ") ON CONFLICT REPLACE)";
    }
}
