package com.podkitsoftware.shoumi.model;

import android.net.Uri;

import com.podkitsoftware.shoumi.Database;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.ForeignKeyAction;
import com.raizlabs.android.dbflow.annotation.ForeignKeyReference;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.container.ForeignKeyContainer;

@Table(databaseName = Database.NAME, tableName = "group_members")
public class GroupMember extends BaseModel {

    public static final Uri BASE_URI = Database.BASE_CONTENT_URI.buildUpon().appendPath(GroupMember$Table.TABLE_NAME).build();

    @Column
    @PrimaryKey
    Long id;

    @Column
    @ForeignKey(
            references = @ForeignKeyReference(columnName = "group_id", columnType = Long.class, foreignColumnName = "id"),
            onDelete = ForeignKeyAction.CASCADE)
    ForeignKeyContainer<Group> group;

    @Column
    @ForeignKey(references = @ForeignKeyReference(columnName = "person_id", columnType = Long.class, foreignColumnName = "id"),
            onDelete = ForeignKeyAction.CASCADE)
    ForeignKeyContainer<Person> person;

    public Uri getUri() {
        return BASE_URI.buildUpon().appendPath(id == null ? "null" : id.toString()).build();
    }

    public Group getGroup() {
        return group.load();
    }

    public Person getPerson() {
        return person.load();
    }
}
