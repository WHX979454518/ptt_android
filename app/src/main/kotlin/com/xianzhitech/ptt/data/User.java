package com.xianzhitech.ptt.data;


import android.support.annotation.Nullable;

public interface User {
    String getId();
    String getName();

    @Nullable
    String getAvatar();
    int getPriority();

    @Nullable
    String getPhoneNumber();
}
