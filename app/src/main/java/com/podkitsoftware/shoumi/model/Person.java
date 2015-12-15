package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.util.CursorUtil;
import com.podkitsoftware.shoumi.util.PrivilegeUtil;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import rx.functions.Func1;

public class Person implements Model, IContactItem, JSONDeserializable {

    public static final String TABLE_NAME = "persons";

    public static final String COL_ID = "person_id";
    public static final String COL_NAME = "person_name";
    public static final String COL_PRIV = "person_priv";
    public static final String COL_AVATAR = "person_avatar";

    String id;
    String name;
    String avatar;
    @Privilege int priv;

    public Person() {
    }

    public Person(final String id, final String name, final String avatar, final @Privilege int priv) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.priv = priv;
    }

    public Person(final String id, final String name) {
        this(id, name, null, 0);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void toValues(ContentValues values) {
        values.put(COL_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_PRIV, priv);
        values.put(COL_AVATAR, avatar);
    }

    @Override
    public void readFrom(Cursor cursor) {
        id = CursorUtil.getString(cursor, COL_ID);
        name = CursorUtil.getString(cursor, COL_NAME);
        avatar = CursorUtil.getString(cursor, COL_AVATAR);
        @Privilege int dbPriv = CursorUtil.getInt(cursor, COL_PRIV);
        priv = dbPriv;
    }

    @Override
    public Person readFrom(final JSONObject object) {
        try {
            id = object.getString("idNumber");
            name = object.getString("name");
            avatar = object.getString("avatar");
            priv = PrivilegeUtil.fromJson(object.getJSONObject("privileges"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return this;
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

    public String getAvatar() {
        return avatar;
    }

    public int getPriv() {
        return priv;
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
                    COL_NAME + " TEXT NOT NULL, " +
                    COL_AVATAR + " TEXT, " +
                    COL_PRIV + " INTEGER NOT NULL" +
                ")";
    }

}
