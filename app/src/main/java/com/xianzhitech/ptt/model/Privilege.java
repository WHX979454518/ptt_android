package com.xianzhitech.ptt.model;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 15/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef(flag = true, value = { Privilege.MAKE_CALL, Privilege.CREATE_GROUP, Privilege.RECEIVE_CALL, Privilege.RECEIVE_GROUP })
public @interface Privilege {
    int MAKE_CALL = 1;
    int CREATE_GROUP = 1 << 1;
    int RECEIVE_CALL = 1 << 2;
    int RECEIVE_GROUP = 1 << 3;
}
