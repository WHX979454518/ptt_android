package com.xianzhitech.ptt.data;


import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.requery.Entity;
import io.requery.Key;
import io.requery.Persistable;
import io.requery.Table;

@AutoValue
@Entity
@Table(name = "users")
public abstract class ContactUser implements User, Persistable, NamedModel {
    @Override
    @Key
    @JsonIgnore
    public abstract String getId();

    @Override
    public abstract String getName();

    @Nullable
    @Override
    public abstract String getAvatar();

    @Override
    public abstract int getPriority();

    @Nullable
    @Override
    public abstract String getPhoneNumber();


    @JsonCreator
    public static ContactUser create(@JsonProperty("phoneNumber") String newPhoneNumber,
                                     @JsonProperty("priority") int newPriority,
                                     @JsonProperty("avatar") String newAvatar,
                                     @JsonProperty("name") String newName,
                                     @JsonProperty("idNumber") String newId) {
        return new AutoValue_ContactUser(newId, newName, newAvatar, newPriority, newPhoneNumber);
    }
}
