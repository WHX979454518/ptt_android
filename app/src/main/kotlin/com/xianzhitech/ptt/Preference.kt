package com.xianzhitech.ptt

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.service.AppParams
import com.xianzhitech.ptt.service.UserToken
import java.util.*

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId: Pair<Uri, Long>?
    var userSessionToken: UserToken?
    var lastLoginUserId: String?
    var lastAppParams : AppParams?
    var lastSyncContactTime : Date?
    var lastIgnoredUpdateUrl : String?
    var deviceId : String?
    val autoExit: Boolean
    var blockCalls: Boolean
    var shortcut : Shortcut
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
