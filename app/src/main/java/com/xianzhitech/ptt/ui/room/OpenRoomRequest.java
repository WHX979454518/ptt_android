package com.xianzhitech.ptt.ui.room;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.xianzhitech.ptt.model.ContactItem;
import com.xianzhitech.ptt.model.Group;
import com.xianzhitech.ptt.model.Person;


/**
 *
 * 打开一个房间的请求
 *
 * Created by fanchao on 18/12/15.
 */
public class OpenRoomRequest implements Parcelable {

    @Nullable
    public final String personId;

    @Nullable
    public final String groupId;

    @Nullable
    public final String conversationId;

    public static OpenRoomRequest ofContact(@NonNull ContactItem item) {
        return new OpenRoomRequest(item instanceof Person ? ((Person) item).getId() : null, item instanceof Group ? ((Group) item).getId() : null, null);
    }

    public static OpenRoomRequest ofRoom(@NonNull String conversationId) {
        return new OpenRoomRequest(null, null, conversationId);
    }

    private OpenRoomRequest(@Nullable final String personId, @Nullable final String groupId, @Nullable final String conversationId) {
        this.personId = personId;
        this.groupId = groupId;
        this.conversationId = conversationId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.personId);
        dest.writeString(this.groupId);
        dest.writeString(this.conversationId);
    }

    protected OpenRoomRequest(Parcel in) {
        this.personId = in.readString();
        this.groupId = in.readString();
        this.conversationId = in.readString();
    }

    public static final Creator<OpenRoomRequest> CREATOR = new Creator<OpenRoomRequest>() {
        public OpenRoomRequest createFromParcel(Parcel source) {
            return new OpenRoomRequest(source);
        }

        public OpenRoomRequest[] newArray(int size) {
            return new OpenRoomRequest[size];
        }
    };
}
