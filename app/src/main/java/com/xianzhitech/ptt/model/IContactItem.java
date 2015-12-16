package com.xianzhitech.ptt.model;

import android.content.Context;
import android.net.Uri;

/**
 *
 * 代表可以用于通讯录的接口
 *
 * Created by fanchao on 14/12/15.
 */
public interface IContactItem extends Model {
    int getTintColor(final Context context);
    String getName();
    Uri getImage();
}
