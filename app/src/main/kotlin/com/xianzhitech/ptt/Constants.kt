package com.xianzhitech.ptt

object Constants {
    const val BLUETOOTH_SCO_RETRY_COUNT = 5
    const val DB_NAME = "app"
    const val DB_VERSION = 1
    const val MAX_CACHE_SIZE = 1024 * 1024 * 10.toLong()

    const val LOGIN_TIMEOUT_SECONDS = 10L
    const val REQUEST_MIC_TIMEOUT_SECONDS: Long = 2
    const val JOIN_ROOM_TIMEOUT_SECONDS: Long = 10

    const val MAX_MEMBER_DISPLAY_COUNT: Int = 3
}
