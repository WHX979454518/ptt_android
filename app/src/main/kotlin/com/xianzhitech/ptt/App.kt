package com.xianzhitech.ptt

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.xianzhitech.ptt.db.AndroidDatabase
import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.connectToService
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.LocalRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.service.sio.SocketIOBackgroundService
import okhttp3.OkHttpClient


open class App : Application(), AppComponent {
    override val httpClient by lazy { OkHttpClient() }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }

    override val userRepository by lazy { LocalRepository(AndroidDatabase(this@App, Constants.DB_NAME, Constants.DB_VERSION)) }
    override val groupRepository: GroupRepository
        get() = userRepository
    override val roomRepository: RoomRepository
        get() = userRepository
    override val contactRepository: ContactRepository
        get() = userRepository
    override val preferenceProvider: PreferenceStorageProvider by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }

    override val btEngine by lazy { BtEngine(this) }
    override val signalServerEndpoint: String
        get() = BuildConfig.SIGNAL_SERVER_ENDPOINT

    override fun connectToBackgroundService() = connectToService<BackgroundServiceBinder>(Intent(this, SocketIOBackgroundService::class.java), BIND_AUTO_CREATE)

    override fun onCreate() {
        super.onCreate()

        //        Picasso.setSingletonInstance(
        //                Picasso.Builder(this).downloader(OkHttpDownloader(httpClient)).build())


    }

    private class SharedPreferenceProvider(private val pref: SharedPreferences) : PreferenceStorageProvider {
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

        companion object {
            const val KEY_USER_TOKEN = "session_token"
            const val KEY_LAST_USER_ID = "last_user_id"
        }
    }
}
