package com.xianzhitech.ptt

import java.util.concurrent.TimeUnit

object Constants {
    @JvmStatic val LOGIN_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val REQUEST_MIC_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 2
    @JvmStatic val JOIN_ROOM_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val UPDATE_ROOM_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10

    @JvmStatic val ROOM_IDLE_TIME_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600L else 30L

    const val MAX_MEMBER_NAME_DISPLAY_COUNT: Int = 3
    const val MAX_MEMBER_ICON_DISPLAY_COUNT: Int = 9

    const val DEFAULT_USER_PRIORITY = 100
    @JvmStatic val SYNC_CONTACT_INTERVAL_MILLS = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES)
    const val HTTP_MAX_CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    const val EMPTY_USER_ID: String = "-1"
}
