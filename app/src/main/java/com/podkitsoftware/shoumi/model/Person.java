package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.podkitsoftware.shoumi.util.CursorUtil;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

import rx.functions.Func1;

@JsonObject
public class Person implements Model {

    public static final String TABLE_NAME = "persons";

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_IS_CONTACT = "is_contact";

    @JsonField(name = "id")
    long id;

    @JsonField(name = "name")
    String name;

    @JsonField(name = "is_contact")
    boolean isContact;

    public Person() {
    }

    public Person(long id, String name, final boolean isContact) {
        this.id = id;
        this.name = name;
        this.isContact = isContact;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_IS_CONTACT, isContact);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getLong(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
        isContact = CursorUtil.getBoolean(cursor, COL_IS_CONTACT);
    }

    @Override
    public String toString() {
        return "Person {id=" + id + ",name=" + name + ",isContact=" + isContact + "}";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isContact() {
        return isContact;
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
                    COL_NAME + " TEXT NOT NULL," +
                    COL_IS_CONTACT + " INTEGER NOT NULL" +
                ")";
    }

    public static class LocaleAwareComparator implements Comparator<Person> {
        private final Collator collator;

        public LocaleAwareComparator() {
            collator = Collator.getInstance();
        }

        public LocaleAwareComparator(final Locale locale) {
            collator = Collator.getInstance(locale);
        }

        @Override
        public int compare(final Person lhs, final Person rhs) {
            return collator.compare(lhs.getName(), rhs.getName());
        }
    }

}
