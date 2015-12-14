package com.podkitsoftware.shoumi.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 对讲房间信息
 *
 * Created by fanchao on 13/12/15.
 */
public class Room implements Parcelable {
    int roomId;
    int localUserId;
    String remoteServer;
    int remotePort;

    public int getRoomId() {
        return roomId;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public int getLocalUserId() {
        return localUserId;
    }

    public String getRemoteServer() {
        return remoteServer;
    }

    @Override
    public String toString() {
        return "Room {id=" + roomId + ",remoteServer=" + remoteServer + ",remotePort=" + remotePort + ",localUserId=" + localUserId + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o instanceof Room) {
            return roomId == ((Room) o).roomId;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return roomId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.roomId);
        dest.writeInt(this.remotePort);
        dest.writeInt(this.localUserId);
        dest.writeString(this.remoteServer);
    }

    public Room() {
    }

    public Room(final int roomId, final int localUserId, final String remoteServer, final int remotePort) {
        this.roomId = roomId;
        this.localUserId = localUserId;
        this.remoteServer = remoteServer;
        this.remotePort = remotePort;
    }

    protected Room(Parcel in) {
        this.roomId = in.readInt();
        this.remotePort = in.readInt();
        this.localUserId = in.readInt();
        this.remoteServer = in.readString();
    }

    public static final Creator<Room> CREATOR = new Creator<Room>() {
        public Room createFromParcel(Parcel source) {
            return new Room(source);
        }

        public Room[] newArray(int size) {
            return new Room[size];
        }
    };
}
