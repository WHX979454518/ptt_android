package com.xianzhitech.ptt.data;

import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;

import java.util.Date;

import io.requery.Column;
import io.requery.Entity;
import io.requery.Index;
import io.requery.Persistable;
import io.requery.Table;

@Entity
@Table(name = "messages")
@AutoValue
@JsonDeserialize(builder = Message.Builder.class)
public abstract class Message implements Persistable {
    @Column(unique = true)
    @Nullable
    @Index
    public abstract String getLocalId();

    @Column(unique = true)
    @Nullable
    @Index
    public abstract String getRemoteId();

    @Index
    public abstract Date getSendTime();

    public abstract String getType();

    @Nullable
    public abstract String getBody();

    @JsonIgnore
    public abstract boolean getHasRead();

    public abstract String getSenderId();

    @Index
    public abstract String getRoomId();

    public static Builder builder() {
        return new AutoValue_Message.Builder()
                .setHasRead(false);
    }

    @AutoValue.Builder
    @JsonPOJOBuilder
    public abstract static class Builder {
        @JsonCreator
        static Builder create() {
            return builder();
        }

        @JsonProperty("localId")
        public abstract Builder setLocalId(String newLocalId);

        @JsonProperty("remoteId")
        public abstract Builder setRemoteId(String newRemoteId);

        @JsonProperty("sendTime")
        public abstract Builder setSendTime(Date newSendTime);

        @JsonProperty("type")
        public abstract Builder setType(String newType);

        @JsonProperty("body")
        public abstract Builder setBody(String newBody);

        @JsonIgnore
        public abstract Builder setHasRead(boolean newHasRead);

        @JsonProperty("senderId")
        public abstract Builder setSenderId(String newSenderId);

        @JsonProperty("roomId")
        public abstract Builder setRoomId(String newRoomId);

        public abstract Message build();
    }
}
