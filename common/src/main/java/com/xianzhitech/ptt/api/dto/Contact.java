package com.xianzhitech.ptt.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.xianzhitech.ptt.data.ContactGroup;
import com.xianzhitech.ptt.data.ContactUser;

import java.util.List;

import io.requery.Persistable;


@AutoValue
public abstract class Contact implements Persistable {
    public abstract List<ContactUser> getMembers();

    public abstract List<ContactGroup> getGroups();

    public abstract long getVersion();

    @JsonCreator
    public static Contact create(@JsonProperty("enterpriseMembers") List<ContactUser> newMembers,
                                 @JsonProperty("enterpriseGroups") List<ContactGroup> newGroups,
                                 @JsonProperty("version") long newVersion) {
        return new AutoValue_Contact(newMembers, newGroups, newVersion);
    }
}
