package com.xianzhitech.ptt

import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider

/**
 * Created by fanchao on 9/01/16.
 */
class MockPreferenceProvider : PreferenceStorageProvider {
    override var userSessionToken: String? = null
    override var lastLoginUserId: String? = null
}