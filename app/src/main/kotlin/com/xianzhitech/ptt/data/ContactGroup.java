package com.xianzhitech.ptt.data;

import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

import io.requery.Entity;
import io.requery.Key;
import io.requery.Persistable;
import io.requery.Table;


@Entity
@Table(name = "groups")
@AutoValue
public abstract class ContactGroup implements Persistable, NamedModel {
    @Key
    public abstract String getId();

    public abstract String getName();

    @Nullable
    public abstract String getAvatar();

    public abstract List<String> getMemberIds();

    @JsonCreator
    public static ContactGroup create(@JsonProperty("members") List<String> newMemberIds,
                                      @JsonProperty("avatar") String newAvatar,
                                      @JsonProperty("name") String newName,
                                      @JsonProperty("idNumber") String newId) {
        return new AutoValue_ContactGroup(newId, newName, newAvatar, newMemberIds);
    }

}
