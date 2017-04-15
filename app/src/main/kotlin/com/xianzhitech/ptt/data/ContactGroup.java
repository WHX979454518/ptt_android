package com.xianzhitech.ptt.data;

import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.xianzhitech.ptt.util.SetConverter;

import java.util.List;
import java.util.Set;

import io.requery.Convert;
import io.requery.Entity;
import io.requery.Key;
import io.requery.Persistable;
import io.requery.Table;


@Entity
@Table(name = "groups")
@AutoValue
@JsonDeserialize(builder = ContactGroup.Builder.class)
public abstract class ContactGroup implements Persistable, NamedModel {
    @Key
    public abstract String getId();

    public abstract String getName();

    @Nullable
    public abstract String getAvatar();

    @Convert(SetConverter.class)
    public abstract Set<String> getMemberIds();

    static Builder builder() {
        return new AutoValue_ContactGroup.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder
    abstract static class Builder {
        @JsonCreator
        static Builder create() {
            return builder();
        }

        @JsonProperty("idNumber")
        public abstract Builder setId(String newId);

        @JsonProperty("name")
        public abstract Builder setName(String newName);

        @JsonProperty("avatar")
        public abstract Builder setAvatar(String newAvatar);

        @JsonProperty("members")
        public abstract Builder setMemberIds(Set<String> newMemberIds);

        public abstract ContactGroup build();
    }
}
