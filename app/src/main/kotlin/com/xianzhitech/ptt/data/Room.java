package com.xianzhitech.ptt.data;

import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;

import java.util.Set;

import io.requery.Entity;
import io.requery.Persistable;
import io.requery.Table;

@AutoValue
@Entity
@Table(name = "rooms")
@JsonDeserialize(builder = Room.Builder.class)
public abstract class Room implements Persistable {
    public abstract String getId();

    public abstract Set<String> getGroups();

    public abstract Set<String> getExtraMembers();

    public abstract String getOwnerId();

    @Nullable
    public abstract String getName();

    public static Builder builder() {
        return new AutoValue_Room.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder
    public abstract static class Builder {
        @JsonCreator
        static Builder create() {
            return builder();
        }

        @JsonProperty("idNumber")
        public abstract Builder setId(String newId);

        @JsonProperty("associatedGroupIds")
        public abstract Builder setGroups(Set<String> newGroups);

        @JsonProperty("extraMemberIds")
        public abstract Builder setExtraMembers(Set<String> newExtraMembers);

        @JsonProperty("ownerId")
        public abstract Builder setOwnerId(String newOwnerId);

        @JsonIgnore
        public abstract Builder setName(String newName);

        public abstract Room build();
    }
}
