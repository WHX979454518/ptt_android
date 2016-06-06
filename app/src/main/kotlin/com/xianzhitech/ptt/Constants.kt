package com.xianzhitech.ptt

object Constants {
    @JvmStatic val LOGIN_TIMEOUT_SECONDS : Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val REQUEST_MIC_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 2
    @JvmStatic val JOIN_ROOM_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val UPDATE_ROOM_TIMEOUT_SECONDS : Long = if (BuildConfig.DEBUG && false) 3600 else 10

    const val ROOM_IDLE_TIME_SECONDS : Long = 30L

    const val MAX_MEMBER_NAME_DISPLAY_COUNT: Int = 3
    const val MAX_MEMBER_ICON_DISPLAY_COUNT: Int = 9

    const val DEFAULT_USER_PRIORITY = 100
}
