package com.xianzhitech.ptt

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.UserCredentials
import com.xianzhitech.ptt.service.UserToken
import org.threeten.bp.LocalTime
import rx.Observable

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId: Pair<Uri, Long>?
    var userSessionToken: UserToken?
    var lastAppParams : AppConfig?
    var lastIgnoredUpdateUrl : String?
    var lastExpPromptTime : Long?
    var contactVersion : Long
    val deviceId : String
    var autoExit: Boolean
    var blockCalls: Boolean
    var playIndicatorSound : Boolean
    var shortcut : Shortcut
    var keepSession : Boolean
    var enableDownTime : Boolean
    var downTimeStart : LocalTime
    var downTimeEnd : LocalTime
    val userSessionTokenSubject : Observable<UserToken>

    var currentUser : CurrentUser?
    var currentUserCredentials : UserCredentials?
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
