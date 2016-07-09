package com.xianzhitech.ptt

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import com.crashlytics.android.Crashlytics
import com.google.gson.Gson
import com.xianzhitech.ptt.media.AudioHandler
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.repo.storage.*
import com.xianzhitech.ptt.service.AppService
import com.xianzhitech.ptt.service.handler.*
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.util.ActivityProviderImpl
import io.fabric.sdk.android.Fabric
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject


open class App : Application(), AppComponent {

    override val httpClient by lazy { onBuildHttpClient().build() }
    override lateinit var userRepository: UserRepository
    override lateinit var groupRepository: GroupRepository
    override lateinit var roomRepository: RoomRepository
    override lateinit var contactRepository: ContactRepository
    override lateinit var preference: Preference
    override lateinit var signalHandler: SignalServiceHandler
    override lateinit var activityProvider: ActivityProvider
    override lateinit var statisticCollector: StatisticCollector
    override lateinit var mediaButtonHandler: MediaButtonHandler
    override val appServerEndpoint = BuildConfig.APP_SERVER_ENDPOINT

    override val appService: AppService by lazy {
        Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(appServerEndpoint)
                .client(httpClient)
                .build()
                .create(AppService::class.java)
    }

    override fun onCreate() {
        super.onCreate()

        Fabric.with(this, Crashlytics())

        preference = AppPreference(this, PreferenceManager.getDefaultSharedPreferences(this), Gson())

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

        signalHandler = SignalServiceHandler(this, this)


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        activityProvider = ActivityProviderImpl().apply {
            registerActivityLifecycleCallbacks(this)
        }

        RoomInvitationHandler(this, signalHandler, userRepository, roomRepository, activityProvider)
        mediaButtonHandler = MediaButtonHandler(signalHandler)
        AudioHandler(this, signalHandler, mediaButtonHandler, httpClient, preference)
        ServiceHandler(this, signalHandler)
        RoomStatusHandler(roomRepository, signalHandler)
        RoomAutoQuitHandler(preference, activityProvider, signalHandler)
        statisticCollector = StatisticCollector(signalHandler)
    }

    open protected fun onBuildHttpClient(): OkHttpClient.Builder {
        return OkHttpClient.Builder().cache(Cache(cacheDir, Constants.HTTP_MAX_CACHE_SIZE))
    }

}
