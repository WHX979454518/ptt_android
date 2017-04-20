package com.xianzhitech.ptt

import android.net.Uri
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.UserCredentials
import org.threeten.bp.LocalTime
import java.util.*

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId: Pair<Uri, Long>?
    var lastIgnoredUpdateUrl : String?
    var lastExpPromptTime : Long?
    var contactVersion : Long
    val deviceId : String
    var autoExit: Boolean
    var blockCalls: Boolean
    var playIndicatorSound : Boolean
    var keepSession : Boolean
    var enableDownTime : Boolean
    var downTimeStart : LocalTime
    var downTimeEnd : LocalTime

    var currentUser : CurrentUser?
    var currentUserCredentials : UserCredentials?

    var lastMessageSyncDate : Date?
}
