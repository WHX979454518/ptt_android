package com.xianzhitech.ptt.service.talk;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 13/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({TalkServiceBinder.ROOM_STATUS_NOT_CONNECTED,
        TalkServiceBinder.ROOM_STATUS_CONNECTING,
        TalkServiceBinder.ROOM_STATUS_ERROR,
        TalkServiceBinder.ROOM_STATUS_CONNECTED,
        TalkServiceBinder.ROOM_STATUS_ACTIVE,
        TalkServiceBinder.ROOM_STATUS_DISCONNECTING})
public @interface RoomStatus {
}
