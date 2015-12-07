package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.podkitsoftware.shoumi.util.CursorUtil;

import rx.functions.Func1;

public class Person implements Model {

    public static final String TABLE_NAME = "persons";

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";

    private long id;
    private String name;

    public Person() {
    }

    public Person(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getLong(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
    }

    @Override
    public String toString() {
        return "Person {id=" + id + ",name=" + name + "}";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        return id == person.id;

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    public static Func1<Cursor, Person> MAPPER = cursor -> {
        final Person person = new Person();
        person.readFrom(cursor);
        return person;
    };

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID + " INTEGER PRIMARY KEY NOT NULL, " +
                    COL_NAME + " TEXT NOT NULL" +
                ")";
    }
}
