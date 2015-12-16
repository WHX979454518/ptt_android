package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.google.common.base.Objects;
import com.podkitsoftware.shoumi.util.CursorUtil;

/**
 *
 * 表示一个会话
 *
 * Created by fanchao on 16/12/15.
 */
public class Conversation implements Model {
    public static final String TABLE_NAME = "conversations";

    public static final String COL_ID = "conv_id";
    public static final String COL_NAME = "conv_name";
    public static final String COL_DESC = "conv_desc";
    public static final String COL_OWNER_ID = "conv_owner_id";
    public static final String COL_PRIORITY = "conv_priority";

    private String id;
    private String name;
    private String description;
    private String ownerId;
    private int priority;


    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void toValues(final ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_DESC, description);
        values.put(COL_OWNER_ID, ownerId);
        values.put(COL_PRIORITY, priority);
    }

    @Override
    public void readFrom(final Cursor cursor) {
        id = CursorUtil.getString(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
        description = CursorUtil.getString(cursor, COL_DESC);
        ownerId = CursorUtil.getString(cursor, COL_OWNER_ID);
        priority = CursorUtil.getInt(cursor, COL_PRIORITY);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Conversation that = (Conversation) o;
        return Objects.equal(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID + " TEXT PRIMARY KEY," +
                    COL_NAME + " TEXT," +
                    COL_DESC + " TEXT," +
                    COL_OWNER_ID + " TEXT NOT NULL REFERENCES " + Person.TABLE_NAME + "(" + Person.COL_ID + ")," +
                    COL_PRIORITY + " INTEGER NOT NULL" +
                ")";
    }
}
