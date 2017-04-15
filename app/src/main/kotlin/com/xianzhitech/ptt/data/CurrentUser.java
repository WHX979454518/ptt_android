package com.xianzhitech.ptt.data;


import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.xianzhitech.ptt.api.event.Event;

import java.util.Map;

@AutoValue
@JsonDeserialize(builder = CurrentUser.Builder.class)
public abstract class CurrentUser implements User, Event, NamedModel {
    @Override
    @JsonProperty("idNumber")
    public abstract String getId();

    @Override
    @JsonProperty("name")
    public abstract String getName();

    @Nullable
    @Override
    @JsonProperty("avatar")
    public abstract String getAvatar();

    @Nullable
    @Override
    @JsonProperty("phoneNumber")
    public abstract String getPhoneNumber();

    @JsonProperty("enterId")
    public abstract String getEnterpriseId();

    @JsonProperty("enterName")
    public abstract String getEnterpriseName();

    @Override
    @JsonIgnore
    public final int getPriority() {
        return Integer.parseInt(getPrivileges().get("priority").toString());
    }

    @JsonProperty("privileges")
    abstract Map<String, Object> getPrivileges();


    @Nullable
    @JsonProperty("locationTime")
    abstract Map<String, Object> getLocationTime();


    @JsonProperty("locationEnable")
    public abstract boolean getLocationEnabled();


    @JsonProperty("locationScanInterval")
    public abstract int getLocationScanIntervalSeconds();


    @JsonProperty("locationReportInterval")
    public abstract int getLocationReportIntervalSeconds();

    @Nullable
    @JsonProperty("locationWeekly")
    abstract int[] getLocationWeekly();

    static Builder builder() {
        return new AutoValue_CurrentUser.Builder()
                .setLocationEnabled(false)
                .setLocationScanIntervalSeconds(-1)
                .setLocationReportIntervalSeconds(-1);
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

        @JsonProperty("phoneNumber")
        public abstract Builder setPhoneNumber(String newPhoneNumber);

        @JsonProperty("enterId")
        public abstract Builder setEnterpriseId(String newEnterpriseId);

        @JsonProperty("enterName")
        public abstract Builder setEnterpriseName(String newEnterpriseName);

        @JsonProperty("privileges")
        public abstract Builder setPrivileges(Map<String, Object> newPrivileges);

        @JsonProperty("locationTime")
        public abstract Builder setLocationTime(Map<String, Object> newLocationTime);

        @JsonProperty("locationEnable")
        public abstract Builder setLocationEnabled(boolean newLocationEnabled);

        @JsonProperty("locationScanInterval")
        public abstract Builder setLocationScanIntervalSeconds(int newLocationScanIntervalSeconds);

        @JsonProperty("locationReportInterval")
        public abstract Builder setLocationReportIntervalSeconds(int newLocationReportIntervalSeconds);

        @JsonProperty("locationWeekly")
        public abstract Builder setLocationWeekly(int[] newLocationWeekly);

        public abstract CurrentUser build();
    }
}
