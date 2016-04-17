package com.xianzhitech.ptt

import android.Manifest
import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import com.xianzhitech.ptt.db.AndroidDatabase
import com.xianzhitech.ptt.db.DatabaseFactory
import com.xianzhitech.ptt.db.TableDefinition
import com.xianzhitech.ptt.engine.BtEngineImpl
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.LocalRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.impl.SignalServiceImpl
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.RoomAutoQuitHandler
import com.xianzhitech.ptt.update.UpdateManager
import com.xianzhitech.ptt.update.UpdateManagerImpl
import okhttp3.OkHttpClient
import rx.subjects.BehaviorSubject


open class App : Application(), AppComponent {

    private var signalServiceSubject: BehaviorSubject<SignalService>? = null

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

    override val btEngine by lazy { BtEngineImpl(this) }
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
            talkEngineProvider = talkEngineProvider,
            btEngine = btEngine)
    }

    override fun onCreate() {
        super.onCreate()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        RoomAutoQuitHandler(this)
    }

    private class SharedPreferenceProvider(private val pref: SharedPreferences) : Preference {
        override var userSessionToken: String?
            get() = pref.getString(KEY_USER_TOKEN, null)
            set(value) {
                pref.edit().putString(KEY_USER_TOKEN, value).apply()
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
            const val KEY_USER_TOKEN = "session_token"
            const val KEY_LAST_USER_ID = "last_user_id"
            const val KEY_BLOCK_CALLS = "block_calls"
            const val KEY_AUTO_EXIT = "auto_exit"
            const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
            const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        }
    }
}
