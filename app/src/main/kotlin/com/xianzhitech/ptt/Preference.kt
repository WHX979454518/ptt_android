package com.xianzhitech.ptt

import android.net.Uri
import com.xianzhitech.ptt.service.UserToken
import java.util.*

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId: Pair<Uri, Long>?
    var userSessionToken: UserToken?
    var lastLoginUserId: String?
    var lastSyncContactTime : Date?
    var blockCalls: Boolean
    var lastIgnoredUpdateUrl : String?
    var deviceId : String?
}