package com.xianzhitech.ptt.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.Set;

import io.requery.Entity;
import io.requery.Persistable;
import io.requery.Table;

@AutoValue
@Entity
@Table(name = "rooms")
public abstract class Room implements Persistable {
    public abstract String getId();

    public abstract Set<String> getGroups();

    public abstract Set<String> getExtraMembers();

    public abstract String getOwnerId();

    public static Room create(@JsonProperty("ownerId") String newOwnerId,
                              @JsonProperty("extraMemberIds") Set<String> newExtraMembers,
                              @JsonProperty("associatedGroupIds") Set<String> newGroups,
                              @JsonProperty("idNumber") String newId) {
        return new AutoValue_Room(newId, newGroups, newExtraMembers, newOwnerId);
    }
}
