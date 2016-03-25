package com.xianzhitech.ptt

/**
 * Created by fanchao on 9/01/16.
 */
class MockPreferenceProvider : Preference {
    override var userSessionToken: String? = null
    override var lastLoginUserId: String? = null
}