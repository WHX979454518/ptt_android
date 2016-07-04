package com.xianzhitech.ptt

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.AppParams
import com.xianzhitech.ptt.service.UserToken

class AppPreference(appContext : Context,
                    private val pref: SharedPreferences,
                    private val gson : Gson) : Preference {

    private val blockCallsKey = R.string.key_block_calls.toFormattedString(appContext)
    private val autoExitKey = R.string.key_auto_exit.toFormattedString(appContext)
    private val playIndicatorSoundKey = R.string.key_play_indicator_sound.toFormattedString(appContext)
    private val keepSessionKey = R.string.key_keep_session.toFormattedString(appContext)

    override var lastIgnoredUpdateUrl: String?
        get() = pref.getString(KEY_IGNORED_UPDATE_URL, null)
        set(value) {
            pref.edit().putString(KEY_IGNORED_UPDATE_URL, value).apply()
        }

    override var userSessionToken: UserToken?
        get() = pref.getString(KEY_USER_TOKEN, null)?.let { gson.fromJson(it, UserToken::class.java) }
        set(value) {
            if (value == null) {
                pref.edit().remove(KEY_USER_TOKEN).apply()
            } else {
                pref.edit().putString(KEY_USER_TOKEN, gson.toJson(value)).apply()
            }
        }

    override var blockCalls: Boolean
        get() = pref.getBoolean(blockCallsKey, true)
        set(value) {
            pref.edit().putBoolean(blockCallsKey, value).apply()
        }

    override var autoExit: Boolean
        get() = pref.getBoolean(autoExitKey, true)
        set(value) {
            pref.edit().putBoolean(autoExitKey, value).apply()
        }

    override var lastLoginUserId: String?
        get() = pref.getString(KEY_LAST_LOGIN_USER_ID, null)
        set(value) {
            pref.edit().putString(KEY_LAST_LOGIN_USER_ID, value).apply()
        }

    override var contactVersion: Long
        get() = pref.getLong(KEY_CONTACT_SYNC_VERSION, Constants.INVALID_CONTACT_VERSION)
        set(value) {
            pref.edit().putLong(KEY_CONTACT_SYNC_VERSION, value).apply()
        }

    override var lastAppParams: AppParams?
        get() = pref.getString(KEY_LAST_APP_PARAMS, null)?.let { gson.fromJson(it, AppParams::class.java) }
        set(value) {
            pref.edit().putString(KEY_LAST_APP_PARAMS, value?.let { gson.toJson(it) }).apply()
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

    override var deviceId: String?
        get() = pref.getString(KEY_DEVICE_ID, null)
        set(value) {
            pref.edit().putString(KEY_DEVICE_ID, value).apply()
        }

    override var playIndicatorSound: Boolean
        get() = pref.getBoolean(playIndicatorSoundKey, true)
        set(value) {
            pref.edit().putBoolean(playIndicatorSoundKey, value).apply()
        }


    override var shortcut: Shortcut
        get() = pref.getString(KEY_SHORTCUT, null)?.let { gson.fromJson(it, Shortcut::class.java) } ?: Shortcut()
        set(value) {
            pref.edit().putString(KEY_SHORTCUT, value.let { gson.toJson(it) } ?: null).apply()
        }

    override var keepSession: Boolean
        get() = pref.getBoolean(keepSessionKey, false)
        set(value) {
            pref.edit().putBoolean(keepSessionKey, value).apply()
        }

    companion object {
        const val KEY_USER_TOKEN = "current_user_token"
        const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
        const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        const val KEY_CONTACT_SYNC_VERSION = "contact_sync_version"
        const val KEY_LAST_LOGIN_USER_ID = "key_last_login_user"
        const val KEY_LAST_APP_PARAMS = "key_last_app_params"
        const val KEY_IGNORED_UPDATE_URL = "key_ignored_update_url"
        const val KEY_DEVICE_ID = "key_device_id"
        const val KEY_SHORTCUT = "key_shortcut"
    }
}