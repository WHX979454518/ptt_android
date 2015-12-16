package com.podkitsoftware.shoumi.model;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 15/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef(value = { GroupType.CONTACTS, GroupType.CONVERSATION})
public @interface GroupType {
    int CONTACTS = 1;
    int CONVERSATION = 0;
}
