package com.podkitsoftware.shoumi.model;

import android.net.Uri;

import com.podkitsoftware.shoumi.Broker;
import com.podkitsoftware.shoumi.Database;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import java.util.List;

import rx.Observable;

@Table(databaseName = Database.NAME, tableName = "groups")
public class Group extends BaseModel {

    public static final Uri BASE_URI = Database.BASE_CONTENT_URI.buildUpon().appendPath(Group$Table.TABLE_NAME).build();

    @Column(name = "id")
    @PrimaryKey
    Long id;

    @Column(name = "name")
    String name;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Uri getUri() {
        return getUri(id);
    }

    public static Uri getUri(Long id) {
        return BASE_URI.buildUpon().appendPath(id == null ? "null" : id.toString()).build();
    }

    public Observable<List<Person>> getMembers() {
        return Broker.INSTANCE.getGroupMembers(this);
    }
}
