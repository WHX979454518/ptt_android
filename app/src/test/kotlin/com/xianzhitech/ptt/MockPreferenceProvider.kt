package com.xianzhitech.ptt

import android.net.Uri

/**
 * Created by fanchao on 9/01/16.
 */
class MockPreferenceProvider : Preference {
    override var userSessionToken: String? = null
    override var lastLoginUserId: String? = null
    override var updateDownloadId: Pair<Uri, Long>?
        get() = throw UnsupportedOperationException()
        set(value) {
        }
    override var blockCalls: Boolean
        get() = throw UnsupportedOperationException()
        set(value) {
        }
    override var autoExit: Boolean
        get() = throw UnsupportedOperationException()
        set(value) {
        }
}