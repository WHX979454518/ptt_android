package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.podkitsoftware.shoumi.Broker;

import java.util.List;

import rx.Observable;
import rx.functions.Func1;

public class Group implements Model {

    public static final String TABLE_NAME = "groups";

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";

    private long id;
    private String name;

    public Group(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Group() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Group group = (Group) o;

        return id == group.id;

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        return "Group {id=" + id + ", name=" + name + "}";
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(COL_ID));
        name = cursor.getString(cursor.getColumnIndex(COL_NAME));
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID   + " INTEGER PRIMARY KEY NOT NULL," +
                    COL_NAME + " TEXT NOT NULL" +
                ")";
    }

    public static Func1<Cursor, Group> MAPPER = cursor -> {
        final Group group = new Group();
        group.readFrom(cursor);
        return group;
    };

    public Observable<List<Person>> getMembers() {
        return Broker.INSTANCE.getGroupMembers(this);
    }
}
