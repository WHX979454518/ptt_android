package com.xianzhitech.ptt.service.talk;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 13/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({TalkBinder.ROOM_STATUS_NOT_CONNECTED,
        TalkBinder.ROOM_STATUS_CONNECTING,
        TalkBinder.ROOM_STATUS_ERROR,
        TalkBinder.ROOM_STATUS_CONNECTED,
        TalkBinder.ROOM_STATUS_ACTIVE,
        TalkBinder.ROOM_STATUS_DISCONNECTING})
public @interface RoomStatus {
}
