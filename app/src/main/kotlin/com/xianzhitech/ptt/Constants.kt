package com.xianzhitech.ptt

import android.content.Context
import com.xianzhitech.ptt.ext.toFormattedString
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

object Constants {
    @JvmStatic val LOGIN_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG) 3600 else 10
    @JvmStatic val REQUEST_MIC_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 2
    @JvmStatic val JOIN_ROOM_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val UPDATE_ROOM_TIMEOUT_SECONDS: Long = if (BuildConfig.DEBUG && false) 3600 else 10
    @JvmStatic val PROMPT_EXP_TIME_INTERVAL_MILLSECONDS : Long = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
    @JvmStatic val EXP_TIME_PROMPT_ADVANCE_MILLSECONDS : Long = TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS)
    @JvmStatic val INVITE_MEMBER_TIME_OUT_MILLS : Long = TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS)
    const val MAX_LOCATIONS_TO_SEND_VIA_WS = 20

    @JvmStatic val ROOM_IDLE_TIME_SECONDS: Long = if (BuildConfig.DEBUG) 3600L else 30L

    @JvmStatic val UTC = ZoneId.of("UTC")
    @JvmStatic val SERVER_TIMEZONE = UTC
    @JvmStatic val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    const val INVALID_CONTACT_VERSION = -1L

    const val MAX_MEMBER_NAME_DISPLAY_COUNT: Int = 3
    const val MAX_MEMBER_ICON_DISPLAY_COUNT: Int = 9

    const val DEFAULT_USER_PRIORITY = 100

    @JvmStatic val SYNC_CONTACT_INTERVAL_MILLS = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES)
//    @JvmStatic val SYNC_CONTACT_INTERVAL_MILLS = TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS)

    const val HTTP_MAX_CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    const val EMPTY_USER_ID: String = "-1"

    fun getAppFullName(context: Context, versionName : String, versionCode : String) : String {
        return "${R.string.app_name.toFormattedString(context)}${getAppFullVersionName(versionName, versionCode)}"
    }

    fun getAppFullVersionName(versionName : String, versionCode : String) : String {
        if (BuildConfig.IS_PRODUCTION) {
            return "v$versionName"
        }
        else {
            return "v$versionName($versionCode)"
        }
    }
}
