package com.xianzhitech.ptt

import android.content.SharedPreferences
import android.net.Uri
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.service.UserToken
import java.util.*

class AppPreference(private val pref: SharedPreferences) : Preference {
    override var userSessionToken: UserToken?
        get() = pref.getString(KEY_USER_TOKEN, null)?.fromBase64ToSerializable() as? UserToken
        set(value) {
            pref.edit().putString(KEY_USER_TOKEN, value?.serializeToBase64()).apply()
        }

    override var blockCalls: Boolean
        get() = pref.getBoolean(KEY_BLOCK_CALLS, true)
        set(value) {
            pref.edit().putBoolean(KEY_BLOCK_CALLS, value).apply()
        }

    override var lastLoginUserId: String?
        get() = pref.getString(KEY_LAST_LOGIN_USER_ID, null)
        set(value) {
            pref.edit().putString(KEY_LAST_LOGIN_USER_ID, value).apply()
        }

    override var updateDownloadId: Pair<Uri, Long>?
        get() = pref.getString(KEY_LAST_UPDATE_DOWNLOAD_URL, null)?.let { Pair(Uri.parse(it), pref.getLong(KEY_LAST_UPDATE_DOWNLOAD_ID, 0)) }
        set(value) {
            pref.edit().apply {
                if (value == null) {
                    remove(KEY_LAST_UPDATE_DOWNLOAD_URL)
                    remove(KEY_LAST_UPDATE_DOWNLOAD_ID)
                } else {
                    putString(KEY_LAST_UPDATE_DOWNLOAD_URL, value.first.toString())
                    putLong(KEY_LAST_UPDATE_DOWNLOAD_ID, value.second)
                }
                apply()
            }
        }

    override var lastSyncContactTime: Date?
        get() = pref.getLong(KEY_LAST_SYNC_TIME, -1).let { if (it  < 0) null else Date(it) }
        set(value) {
            if (value == null) {
                pref.edit().remove(KEY_LAST_SYNC_TIME).apply()
            } else {
                pref.edit().putLong(KEY_LAST_SYNC_TIME, value.time).apply()
            }
        }

    companion object {
        const val KEY_USER_TOKEN = "user_token"
        const val KEY_BLOCK_CALLS = "block_calls"
        const val KEY_LAST_SYNC_TIME = "last_sync_contact_time"
        const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
        const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        const val KEY_LAST_LOGIN_USER_ID = "key_last_login_user"
    }
}