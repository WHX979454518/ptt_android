package com.xianzhitech.ptt

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import com.xianzhitech.ptt.bluetooth.BluetoothHandler
import com.xianzhitech.ptt.db.AndroidDatabase
import com.xianzhitech.ptt.db.DatabaseFactory
import com.xianzhitech.ptt.db.TableDefinition
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.LocalRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.UserToken
import com.xianzhitech.ptt.service.impl.SignalServiceImpl
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.RoomAutoQuitHandler
import com.xianzhitech.ptt.ui.invite.RoomInvitationReceiver
import com.xianzhitech.ptt.update.UpdateManager
import com.xianzhitech.ptt.update.UpdateManagerImpl
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference


open class App : Application(), AppComponent, ActivityProvider {

    override val httpClient by lazy { OkHttpClient() }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }

    override val userRepository by lazy {
        LocalRepository(object : DatabaseFactory {
            override fun createDatabase(tables: Array<TableDefinition>, version: Int) = AndroidDatabase(this@App, tables, Constants.DB_NAME, version)
        })
    }
    override val groupRepository: GroupRepository
        get() = userRepository
    override val roomRepository: RoomRepository
        get() = userRepository
    override val contactRepository: ContactRepository
        get() = userRepository
    override val preference: Preference by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }

    override val signalServerEndpoint: String
        get() = BuildConfig.SIGNAL_SERVER_ENDPOINT

    override val updateManager: UpdateManager = UpdateManagerImpl(this, Uri.parse(BuildConfig.UPDATE_SERVER_ENDPOINT))

    override val signalService by lazy { SignalServiceImpl(
            appContext = this,
            signalServerEndpoint = BuildConfig.SIGNAL_SERVER_ENDPOINT,
            preference = preference,
            userRepository = userRepository,
            groupRepository = groupRepository,
            contactRepository = contactRepository,
            roomRepository = roomRepository,
            talkEngineProvider = talkEngineProvider)
    }

    override val activityProvider = this

    private var currentStartedActivityReference = WeakReference<Activity>(null)

    override fun onCreate() {
        super.onCreate()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        RoomAutoQuitHandler(this)
        RoomInvitationReceiver(this, activityProvider)

        registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {
            override fun onActivityStarted(activity: Activity) {
                currentStartedActivityReference = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (currentStartedActivity == activity) {
                    currentStartedActivityReference = WeakReference<Activity>(null)
                }
            }
        })
        BluetoothHandler(this, signalService)
    }

    override val currentStartedActivity: Activity?
        get() = currentStartedActivityReference.get()


    private class SharedPreferenceProvider(private val pref: SharedPreferences) : Preference {
        override var userSessionToken: UserToken?
            get() = pref.getString(KEY_USER_TOKEN, null)?.fromBase64ToSerializable() as? UserToken
            set(value) {
                pref.edit().putString(KEY_USER_TOKEN, value?.serializeToBase64()).apply()
            }

        override var lastLoginUserId: String?
            get() = pref.getString(KEY_LAST_USER_ID, null)
            set(value) {
                pref.edit().putString(KEY_LAST_USER_ID, value).apply()
            }

        override var blockCalls: Boolean
            get() = pref.getBoolean(KEY_BLOCK_CALLS, true)
            set(value) {
                pref.edit().putBoolean(KEY_BLOCK_CALLS, value).apply()
            }

        override var autoExit: Boolean
            get() = pref.getBoolean(KEY_AUTO_EXIT, true)
            set(value) {
                pref.edit().putBoolean(KEY_AUTO_EXIT, value).apply()
            }

        override var updateDownloadId: Pair<Uri, Long>?
            get() = pref.getString(KEY_LAST_UPDATE_DOWNLOAD_URL, null) ?.let { Pair(Uri.parse(it), pref.getLong(KEY_LAST_UPDATE_DOWNLOAD_ID, 0)) }
            set(value) {
                pref.edit().apply {
                    if (value == null) {
                        remove(KEY_LAST_UPDATE_DOWNLOAD_URL)
                        remove(KEY_LAST_UPDATE_DOWNLOAD_ID)
                    }
                    else {
                        putString(KEY_LAST_UPDATE_DOWNLOAD_URL, value.first.toString())
                        putLong(KEY_LAST_UPDATE_DOWNLOAD_ID, value.second)
                    }
                    apply()
                }
            }

        companion object {
            const val KEY_USER_TOKEN = "user_token"
            const val KEY_LAST_USER_ID = "last_user_id"
            const val KEY_BLOCK_CALLS = "block_calls"
            const val KEY_AUTO_EXIT = "auto_exit"
            const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
            const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        }
    }
}
