package com.xianzhitech.ptt.service.user;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 17/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({LoginStatus.IDLE, LoginStatus.LOGIN_IN_PROGRESS, LoginStatus.LOGGED_ON})
public @interface LoginStatus {
    int IDLE = 0;
    int LOGIN_IN_PROGRESS = 1;
    int LOGGED_ON = 2;
}
