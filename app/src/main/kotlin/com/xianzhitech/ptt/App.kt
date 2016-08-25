package com.xianzhitech.ptt

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import ch.qos.logback.classic.Level
import com.crashlytics.android.Crashlytics
import com.google.gson.Gson
import com.xianzhitech.ptt.ext.ImmediateMainThreadScheduler
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.Scheduler
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
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

    var isPushProcess : Boolean = false
    private set

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
        instance = this
        super.onCreate()

        RxAndroidPlugins.getInstance().registerSchedulersHook(object : RxAndroidSchedulersHook() {
            private val scheduler = ImmediateMainThreadScheduler()

            override fun getMainThreadScheduler(): Scheduler {
                return scheduler
            }
        })

        if (BuildConfig.DEBUG.not()) {
            Fabric.with(this, Crashlytics())
        }
        else {
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? ch.qos.logback.classic.Logger)?.level = Level.ALL
        }

        MDC.put("pid", Process.myPid().toString())
        MDC.put("version", BuildConfig.VERSION_NAME)

        val am = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        val pid = Process.myPid()
        val currentProcessName = am.runningAppProcesses.first { it.pid == pid }.processName
        if (currentProcessName.contains("push").not()) {
            isPushProcess = false
            preference = AppPreference(this, PreferenceManager.getDefaultSharedPreferences(this), Gson())

            val helper = createSQLiteStorageHelper(this, "data")
            val userStorage = UserSQLiteStorage(helper)
            val groupStorage = GroupSQLiteStorage(helper)
            val userNotification = PublishSubject.create<Unit>()
            val groupNotification = PublishSubject.create<Unit>()
            val roomNotification = PublishSubject.create<Unit>()

            userRepository = UserRepository(userStorage, userNotification)
            groupRepository = GroupRepository(groupStorage, groupNotification)
            roomRepository = RoomRepository(RoomSQLiteStorage(helper), groupStorage,
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

            mediaButtonHandler = MediaButtonHandler(signalHandler)
            AudioHandler(this, signalHandler, mediaButtonHandler, httpClient, preference)
            ServiceHandler(this, this)
            RoomStatusHandler(roomRepository, signalHandler)
            RoomAutoQuitHandler(preference, activityProvider, signalHandler)
            statisticCollector = StatisticCollector(signalHandler)
        } else {
            isPushProcess = true
        }
    }

    open protected fun onBuildHttpClient(): OkHttpClient.Builder {
        return OkHttpClient.Builder().cache(Cache(cacheDir, Constants.HTTP_MAX_CACHE_SIZE))
    }

    companion object {
        lateinit var instance : App
        private set
    }

}
