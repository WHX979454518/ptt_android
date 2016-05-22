package com.xianzhitech.ptt

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import com.xianzhitech.ptt.bluetooth.AudioHandler
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.repo.storage.ContactSQLiteStorage
import com.xianzhitech.ptt.repo.storage.GroupLRUCacheStorage
import com.xianzhitech.ptt.repo.storage.GroupSQLiteStorage
import com.xianzhitech.ptt.repo.storage.RoomLRUCacheStorage
import com.xianzhitech.ptt.repo.storage.RoomSQLiteStorage
import com.xianzhitech.ptt.repo.storage.UserLRUCacheStorage
import com.xianzhitech.ptt.repo.storage.UserSQLiteStorage
import com.xianzhitech.ptt.repo.storage.createSQLiteStorageHelper
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.UserToken
import com.xianzhitech.ptt.service.impl.IOSignalService
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.RoomAutoQuitHandler
import com.xianzhitech.ptt.ui.invite.RoomInvitationReceiver
import com.xianzhitech.ptt.ui.service.ServiceHandler
import com.xianzhitech.ptt.update.UpdateManager
import com.xianzhitech.ptt.update.UpdateManagerImpl
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference


open class App : Application(), AppComponent {

    override val httpClient by lazy { OkHttpClient() }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }


    override lateinit var userRepository : UserRepository
    override lateinit var groupRepository: GroupRepository
    override lateinit var roomRepository: RoomRepository
    override lateinit var contactRepository: ContactRepository

    override val preference: Preference by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }

    override val signalServerEndpoint: String
        get() = BuildConfig.SIGNAL_SERVER_ENDPOINT

    override val updateManager: UpdateManager = UpdateManagerImpl(this, Uri.parse(BuildConfig.UPDATE_SERVER_ENDPOINT))

    override lateinit var signalService : SignalService

    override lateinit var activityProvider : ActivityProvider

    private var currentStartedActivityReference = WeakReference<Activity>(null)

    override fun onCreate() {
        super.onCreate()

        val helper = createSQLiteStorageHelper(this, "data")
        val userStorage = UserLRUCacheStorage(UserSQLiteStorage(helper))
        val groupStorage = GroupLRUCacheStorage(GroupSQLiteStorage(helper))
        userRepository = UserRepository(this, userStorage)
        groupRepository = GroupRepository(this, groupStorage)
        roomRepository = RoomRepository(this, RoomLRUCacheStorage(RoomSQLiteStorage(helper)), groupStorage, userStorage)
        contactRepository = ContactRepository(this, ContactSQLiteStorage(helper, userStorage, groupStorage))

        signalService = IOSignalService(
                context = this,
                endpoint = BuildConfig.SIGNAL_SERVER_ENDPOINT,
                preference = preference,
                userRepository = userRepository,
                contactRepository = contactRepository,
                roomRepository = roomRepository)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        activityProvider = ActivityProviderImpl().apply {
            registerActivityLifecycleCallbacks(this)
        }

        RoomAutoQuitHandler(this)
        RoomInvitationReceiver(this, signalService, activityProvider)
        AudioHandler(this, signalService, activityProvider)
        ServiceHandler(this, signalService)
    }

    private class SharedPreferenceProvider(private val pref: SharedPreferences) : Preference {
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
            const val KEY_BLOCK_CALLS = "block_calls"
            const val KEY_AUTO_EXIT = "auto_exit"
            const val KEY_LAST_UPDATE_DOWNLOAD_URL = "last_update_download_url"
            const val KEY_LAST_UPDATE_DOWNLOAD_ID = "last_update_download_id"
        }
    }
}
