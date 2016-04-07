package com.xianzhitech.ptt

import android.net.Uri

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId : Pair<Uri, Long>?
    var userSessionToken: String?
    var lastLoginUserId: String?
    var blockCalls : Boolean
    var autoExit : Boolean
}