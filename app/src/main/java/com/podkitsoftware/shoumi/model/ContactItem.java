package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.Nullable;

import com.podkitsoftware.shoumi.util.CursorUtil;

import org.apache.commons.lang3.StringUtils;

import rx.functions.Func1;

/**
 *
 * 表示一个通讯录项目. 可以是一个组, 也可以是一个人
 *
 * Created by fanchao on 14/12/15.
 */
public class ContactItem implements Model {

    public static final String TABLE_NAME = "contacts";

    public static final String COL_GROUP_ID = "contact_group_id";
    public static final String COL_PERSON_ID = "contact_person_id";

    @Nullable String groupId;
    @Nullable String personId;

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (o instanceof ContactItem) {
            return StringUtils.equals(((ContactItem) o).groupId, groupId) &&
                    StringUtils.equals(((ContactItem) o).personId, personId);
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = groupId != null ? groupId.hashCode() : 0;
        result = 31 * result + (personId != null ? personId.hashCode() : 0);
        return result;
    }

    @Override
    public void toValues(final ContentValues values) {
        values.put(COL_GROUP_ID, groupId);
        values.put(COL_PERSON_ID, personId);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public String toString() {
        return "ContactItem{" +
                "groupId='" + groupId + '\'' +
                ", personId='" + personId + '\'' +
                '}';
    }

    @Override
    public void readFrom(final Cursor cursor) {
        groupId = CursorUtil.getString(cursor, COL_GROUP_ID);
        personId = CursorUtil.getString(cursor, COL_PERSON_ID);
    }

    public boolean isValid() {
        return StringUtils.isNotEmpty(groupId) || StringUtils.isNotEmpty(personId);
    }

    public boolean isGroup() {
        return StringUtils.isNotEmpty(groupId);
    }

    public boolean isPerson() {
        return StringUtils.isNotEmpty(personId);
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COL_GROUP_ID + " TEXT UNIQUE ON CONFLICT IGNORE REFERENCES " + Group.TABLE_NAME + "(" + Group.COL_ID + ") ON DELETE CASCADE," +
                COL_PERSON_ID + " TEXT UNIQUE ON CONFLICT IGNORE REFERENCES " + Person.TABLE_NAME + "(" + Person.COL_ID + ") ON DELETE CASCADE" +
                ")";
    }

    public static Func1<Cursor, ContactItem> MAPPER = cursor -> {
        final ContactItem item = new ContactItem();
        item.readFrom(cursor);
        return item;
    };


    public static Func1<Cursor, IContactItem> REAL_ITEM_MAPPER = cursor -> {
        final ContactItem item = MAPPER.call(cursor);
        if (!item.isValid()) {
            throw new IllegalStateException("Invalid contact item: " + item);
        }

        if (item.isGroup()) {
            return Group.MAPPER.call(cursor);
        }
        else {
            return Person.MAPPER.call(cursor);
        }

    };
}
