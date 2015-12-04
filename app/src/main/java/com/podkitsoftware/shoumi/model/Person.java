package com.podkitsoftware.shoumi.model;

import android.net.Uri;

import com.podkitsoftware.shoumi.Database;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(databaseName = Database.NAME, tableName = "persons")
public class Person extends BaseModel {

    public static final Uri BASE_URI = Database.BASE_CONTENT_URI.buildUpon().appendPath(Person$Table.TABLE_NAME).build();

    @PrimaryKey
    @Column(name = "id")
    Long id;

    @Column(name = "name")
    String name;

    public Uri getUri() {
        return BASE_URI.buildUpon().appendPath(id == null ? "null" : id.toString()).build();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
