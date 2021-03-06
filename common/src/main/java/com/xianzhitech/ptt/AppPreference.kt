package com.xianzhitech.ptt

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.fasterxml.jackson.databind.ObjectMapper
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.UserCredentials
import com.xianzhitech.ptt.ext.toFormattedString
import org.threeten.bp.LocalTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

class AppPreference(appContext : Context,
                    private val pref: SharedPreferences,
                    private val appComponent: AppComponent) : Preference {

    private val objectMapper : ObjectMapper
    get() = appComponent.objectMapper


    private val blockCallsKey = R.string.key_block_calls.toFormattedString(appContext)
    private val autoExitKey = R.string.key_auto_exit.toFormattedString(appContext)
    private val playIndicatorSoundKey = R.string.key_play_indicator_sound.toFormattedString(appContext)
    private val keepSessionKey = R.string.key_keep_session.toFormattedString(appContext)
    private val downTimeEnableKey = R.string.key_enable_downtime.toFormattedString(appContext)
    private val downTimeStartKey = R.string.key_downtime_start.toFormattedString(appContext)
    private val downTimeEndKey = R.string.key_downtime_end.toFormattedString(appContext)

    override var lastIgnoredUpdateUrl: String?
        get() = pref.getString(KEY_IGNORED_UPDATE_URL, null)
        set(value) {
            pref.edit().putString(KEY_IGNORED_UPDATE_URL, value).apply()
        }

    override var blockCalls: Boolean
        get() = pref.getBoolean(blockCallsKey, false)
        set(value) {
            pref.edit().putBoolean(blockCallsKey, value).apply()
        }

    override var autoExit: Boolean
        get() = pref.getBoolean(autoExitKey, true)
        set(value) {
            pref.edit().putBoolean(autoExitKey, value).apply()
        }

    var lastLoginUserId: String?
        get() = pref.getString(KEY_LAST_LOGIN_USER_ID, null)
        set(value) {
            pref.edit().putString(KEY_LAST_LOGIN_USER_ID, value).apply()
        }

    override var contactVersion: Long
        get() = pref.getLong(KEY_CONTACT_SYNC_VERSION, Constants.INVALID_CONTACT_VERSION)
        set(value) {
            pref.edit().putLong(KEY_CONTACT_SYNC_VERSION, value).apply()
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

    override val deviceId: String
        get() {
            var id = pref.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                pref.edit().putString(KEY_DEVICE_ID, id).apply()
            }

            return id
        }

    override var playIndicatorSound: Boolean
        get() = pref.getBoolean(playIndicatorSoundKey, true)
        set(value) {
            pref.edit().putBoolean(playIndicatorSoundKey, value).apply()
        }

    override var keepSession: Boolean
        get() = pref.getBoolean(keepSessionKey, false)
        set(value) {
            pref.edit().putBoolean(keepSessionKey, value).apply()
        }

    override var lastExpPromptTime: Long?
        get() = pref.getLong(KEY_LAST_EXP_PROMPT_TIME, 0).let { if (it > 0) it else null }
        set(value) {
            if (value != null) {
                pref.edit().putLong(KEY_LAST_EXP_PROMPT_TIME, value).apply()
            }
            else {
                pref.edit().remove(KEY_LAST_EXP_PROMPT_TIME).apply()
            }
        }

    override var downTimeStart: LocalTime
        get() = pref.getString(downTimeStartKey, null)?.let { LocalTime.parse(it, Constants.TIME_FORMAT) } ?: DEFAULT_DOWNTIME_START
        set(value) {
            pref.edit().putString(downTimeStartKey, value.toString()).apply()
        }

    override var downTimeEnd: LocalTime
        get() = pref.getString(downTimeEnableKey, null)?.let { LocalTime.parse(it, Constants.TIME_FORMAT) } ?: DEFAULT_DOWNTIME_END
        set(value) {
            pref.edit().putString(downTimeEndKey, value.toString()).apply()
        }

    override var enableDownTime: Boolean
        get() = pref.getBoolean(downTimeEnableKey, false)
        set(value) {
            pref.edit().putBoolean(downTimeEnableKey, value).apply()
        }

    override var currentUser: CurrentUser? by sharePreference("current_user")
    override var currentUserCredentials: UserCredentials? by sharePreference("current_user_credentials")
    override var lastMessageSyncDate: Date? by sharePreference("last_message_sync")

    private inline fun <reified T> sharePreference(key: String) : SharedPreferenceDelegate<T> {
        return SharedPreferenceDelegate(key, pref, T::class.java, objectMapper)
    }

    private class SharedPreferenceDelegate<T>(val key : String,
                                              val pref: SharedPreferences,
                                              val clazz : Class<T>,
                                              val objectMapper: ObjectMapper) {
        private var cacheValue : AtomicReference<T?>? = null

        operator fun getValue(thisRef: Any?, property : KProperty<*>) : T? {
            if (cacheValue == null) {
                cacheValue = AtomicReference(pref.getString(key, null)?.let { objectMapper.readValue(it, clazz) })
            }

            return cacheValue!!.get()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value : T?) {
            val editor = pref.edit()
            if (value == null) {
                editor.remove(key)
            }
            else {
                editor.putString(key, objectMapper.writeValueAsString(value))
            }

            editor.apply()
            cacheValue?.set(value)
        }
    }

    companion object {
        const val KEY_USER_TOKEN = "current_user_token"
        const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
        const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        const val KEY_CONTACT_SYNC_VERSION = "contact_sync_version"
        const val KEY_LAST_LOGIN_USER_ID = "key_last_login_user"
        const val KEY_LAST_APP_CONFIG = "key_last_app_config"
        const val KEY_IGNORED_UPDATE_URL = "key_ignored_update_url"
        const val KEY_DEVICE_ID = "key_device_id"
        const val KEY_SHORTCUT = "key_shortcut"
        const val KEY_LAST_EXP_PROMPT_TIME = "key_last_exp_prompt_time"

        @JvmStatic val DEFAULT_DOWNTIME_START = LocalTime.of(23, 0)
        @JvmStatic val DEFAULT_DOWNTIME_END = LocalTime.of(7, 30)
    }
}