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
public class Person implements Model, IContactItem {

    public static final String TABLE_NAME = "persons";

    public static final String COL_ID = "person_id";
    public static final String COL_NAME = "person_name";

    @JsonField(name = "id")
    String id;

    @JsonField(name = "name")
    String name;

    public Person() {
    }

    public Person(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getString(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
    }

    @Override
    public String toString() {
        return "Person {id=" + id + ",name=" + name +  "}";
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getTintColor(final Context context) {
        final int[] colors = context.getResources().getIntArray(R.array.account_colors);
        return colors[id.hashCode() % colors.length];
    }

    @Override
    public Uri getImage() {
        //TODO: 生成图像
        return Uri.parse("http://icons.iconarchive.com/icons/hopstarter/face-avatars/256/Male-Face-F5-icon.png");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Person person = (Person) o;
        return StringUtils.equals(person.id, id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    public static Func1<Cursor, Person> MAPPER = cursor -> {
        final Person person = new Person();
        person.readFrom(cursor);
        return person;
    };

    public static String getCreateTableSql() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID + " TEXT PRIMARY KEY NOT NULL, " +
                    COL_NAME + " TEXT NOT NULL" +
                ")";
    }

}
