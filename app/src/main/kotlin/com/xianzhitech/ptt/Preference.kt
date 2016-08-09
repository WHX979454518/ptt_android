package com.xianzhitech.ptt

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.service.AppConfig
import com.xianzhitech.ptt.service.UserToken

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId: Pair<Uri, Long>?
    var userSessionToken: UserToken?
    var lastLoginUserId: String?
    var lastAppParams : AppConfig?
    var lastIgnoredUpdateUrl : String?
    var lastExpPromptTime : Long?
    var contactVersion : Long
    var deviceId : String?
    var autoExit: Boolean
    var blockCalls: Boolean
    var playIndicatorSound : Boolean
    var shortcut : Shortcut
    var keepSession : Boolean
}


enum class ShortcutMode {
    @SerializedName("no_op")
    NO_OP,

    @SerializedName("user")
    USER,

    @SerializedName("group")
    GROUP,

    @SerializedName("room")
    ROOM
}

data class Shortcut(@SerializedName("mode") val mode : ShortcutMode = ShortcutMode.NO_OP,
                    @SerializedName("id") val id : String = "")
