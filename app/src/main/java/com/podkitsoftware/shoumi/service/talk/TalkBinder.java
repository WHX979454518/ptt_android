package com.podkitsoftware.shoumi.service.talk;

import android.os.IBinder;

/**
 * 获取对讲服务中房间信息的接口
 *
 * Created by fanchao on 13/12/15.
 */
public interface TalkBinder extends IBinder {
    int ROOM_STATUS_NOT_CONNECTED = 0;
    int ROOM_STATUS_ERROR = 1;
    int ROOM_STATUS_CONNECTING = 2;
    int ROOM_STATUS_CONNECTED = 3;
    int ROOM_STATUS_ACTIVE = 4;
    int ROOM_STATUS_DISCONNECTING = 5;

    @RoomStatus int getCurrRoomStatus();
    String getCurrGroupId();
}
