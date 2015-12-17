package com.xianzhitech.ptt.service.talk;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 13/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({RoomStatus.NOT_CONNECTED,
        RoomStatus.CONNECTING,
        RoomStatus.CONNECTED,
        RoomStatus.REQUESTING_MIC,
        RoomStatus.ACTIVE,
        RoomStatus.RELEASING_MIC,
        RoomStatus.DISCONNECTING})
public @interface RoomStatus {
    int NOT_CONNECTED = 0;
    int CONNECTING = 1;
    int CONNECTED = 2;
    int REQUESTING_MIC = 3;
    int ACTIVE = 4;
    int RELEASING_MIC = 5;
    int DISCONNECTING = 6;
}
