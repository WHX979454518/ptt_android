package com.xianzhitech.ptt

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.media.AudioHandler
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.repo.storage.*
import com.xianzhitech.ptt.service.AppParams
import com.xianzhitech.ptt.service.AppRequest
import com.xianzhitech.ptt.service.AppService
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.handler.RoomInvitationHandler
import com.xianzhitech.ptt.service.handler.RoomStatusHandler
import com.xianzhitech.ptt.service.handler.ServiceHandler
import com.xianzhitech.ptt.service.handler.StatisticCollector
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.PhoneCallHandler
import okhttp3.OkHttpClient
import rx.Single
import rx.subjects.PublishSubject


open class App : Application(), AppComponent {

    override val httpClient by lazy { OkHttpClient() }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }

    override lateinit var userRepository : UserRepository
    override lateinit var groupRepository: GroupRepository
    override lateinit var roomRepository: RoomRepository
    override lateinit var contactRepository: ContactRepository

    override lateinit var preference: Preference

//    override val updateManager: UpdateManager = UpdateManagerImpl(this, Uri.parse(BuildConfig.UPDATE_SERVER_ENDPOINT))
    override lateinit var signalService : SignalService
    override lateinit var activityProvider : ActivityProvider
    override lateinit var statisticCollector: StatisticCollector

    override val appService: AppService = object : AppService {
        override fun retrieveAppParams(request: AppRequest): Single<AppParams> {
            return Single.just(AppParams(null, false, null, "http://netptt.cn:20000"))
        }
    }

    override fun onCreate() {
        super.onCreate()

        preference = AppPreference(PreferenceManager.getDefaultSharedPreferences(this))

        val helper = createSQLiteStorageHelper(this, "data")
        val userStorage = UserLRUCacheStorage(UserSQLiteStorage(helper))
        val groupStorage = GroupLRUCacheStorage(GroupSQLiteStorage(helper))
        val userNotification = PublishSubject.create<Unit>()
        val groupNotification = PublishSubject.create<Unit>()
        val roomNotification = PublishSubject.create<Unit>()

        userRepository = UserRepository(userStorage, userNotification)
        groupRepository = GroupRepository(groupStorage, groupNotification)
        roomRepository = RoomRepository(RoomLRUCacheStorage(RoomSQLiteStorage(helper)), groupStorage,
                userStorage, roomNotification, userNotification, groupNotification)
        contactRepository = ContactRepository(ContactSQLiteStorage(helper, userStorage, groupStorage), userNotification, groupNotification)

        signalService = AppParamSignalServiceWrapper(
                appContext = this,
                userRepository = userRepository,
                groupRepository = groupRepository,
                roomRepository = roomRepository,
                contactRepository = contactRepository,
                preference = preference,
                appService = appService
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        activityProvider = ActivityProviderImpl().apply {
            registerActivityLifecycleCallbacks(this)
        }

        RoomInvitationHandler(this, signalService, userRepository, roomRepository, activityProvider)
        AudioHandler(this, signalService, talkEngineProvider, activityProvider)
        ServiceHandler(this, signalService)
        RoomStatusHandler(roomRepository, signalService)
        statisticCollector = StatisticCollector(signalService)
    }

}
