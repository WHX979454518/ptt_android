package com.podkitsoftware.shoumi.model;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by fanchao on 15/12/15.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef(value = { GroupType.PRE_DEFINED, GroupType.TEMPORARY })
public @interface GroupType {
    int PRE_DEFINED = 0;
    int TEMPORARY = 1;
}
