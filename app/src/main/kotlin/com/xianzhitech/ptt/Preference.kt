package com.xianzhitech.ptt

import android.net.Uri
import com.xianzhitech.ptt.service.UserToken

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var updateDownloadId : Pair<Uri, Long>?
    var userSessionToken: UserToken?
    var lastLoginUserId : String?
    var blockCalls : Boolean
}