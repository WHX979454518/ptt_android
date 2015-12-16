package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.podkitsoftware.shoumi.util.CursorUtil;

/**
 * 表示会话列表里的成员
 *
 * Created by fanchao on 16/12/15.
 */
public class ConversationMember implements Model {
    public static final String TABLE_NAME = "conversation_members";

    public static final String COL_CONVERSATION_ID = "cm_conv_id";
    public static final String COL_PERSON_ID = "cm_person_id";

    private String conversationId;
    private String personId;

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void toValues(final ContentValues values) {
        values.put(COL_CONVERSATION_ID,  conversationId);
        values.put(COL_PERSON_ID, personId);
    }

    @Override
    public void readFrom(final Cursor cursor) {
        conversationId = CursorUtil.getString(cursor, COL_CONVERSATION_ID);
        personId = CursorUtil.getString(cursor, COL_PERSON_ID);
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_CONVERSATION_ID + " TEXT NOT NULL REFERENCES " + Conversation.TABLE_NAME + "(" + Conversation.COL_ID + ")," +
                    COL_PERSON_ID + " TEXT NOT NULL REFERENCES " + Person.TABLE_NAME + "(" + Person.COL_ID + ")" +
                ")";
    }
}