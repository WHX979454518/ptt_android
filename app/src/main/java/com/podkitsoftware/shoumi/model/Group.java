package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.util.CursorUtil;

import org.apache.commons.lang3.StringUtils;

import rx.functions.Func1;

@JsonObject
public class Group implements Model, Cloneable, IContactItem {

    public static final String TABLE_NAME = "groups";

    public static final String COL_ID = "group_id";
    public static final String COL_NAME = "group_name";
    public static final String COL_PRIORITY = "group_priority";

    @JsonField(name = "id")
    String id;

    @JsonField(name = "name")
    String name;

    @JsonField(name = "priority")
    int priority;

    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Group() {
    }

    @Override
    public Group clone() {
        try {
            return (Group) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Group group = (Group) o;
        return StringUtils.equals(group.id, id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "Group {id=" + id + ", name=" + name + "}";
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_PRIORITY, priority);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getString(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
        priority = CursorUtil.getInt(cursor, COL_PRIORITY);
    }

    @Override
    public int getTintColor(final Context context) {
        final int[] colors = context.getResources().getIntArray(R.array.account_colors);
        return colors[id.hashCode() % colors.length];
    }

    @Override
    public Uri getImage() {
        //TODO: 生成组的图像
        return Uri.parse("http://icons.iconarchive.com/icons/hopstarter/face-avatars/256/Male-Face-F5-icon.png");
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID   + " INTEGER PRIMARY KEY NOT NULL," +
                    COL_NAME + " TEXT NOT NULL," +
                    COL_PRIORITY + " INTEGER NOT NULL" +
                ")";
    }

    public static Func1<Cursor, Group> MAPPER = cursor -> {
        final Group group = new Group();
        group.readFrom(cursor);
        return group;
    };
}
