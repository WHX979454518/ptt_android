package com.xianzhitech.ptt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.preference.PreferenceManager
import android.support.multidex.MultiDexApplication
import android.support.v4.app.ActivityCompat
import ch.qos.logback.classic.Level
import com.baidu.mapapi.SDKInitializer
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.GlideModule
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule
import com.google.gson.Gson
import com.jakewharton.threetenabp.AndroidThreeTen
import com.xianzhitech.ptt.api.AppApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Storage
import com.xianzhitech.ptt.ext.ImmediateMainThreadScheduler
import com.xianzhitech.ptt.media.AudioHandler
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.repo.*
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
import org.webrtc.PeerConnectionFactory
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import rx.Scheduler
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.io.InputStream


open class App : MultiDexApplication(), AppComponent {

    override val httpClient : OkHttpClient by lazy { onBuildHttpClient().build() }
    override lateinit var userRepository: UserRepository
    override lateinit var groupRepository: GroupRepository
    override lateinit var roomRepository: RoomRepository
    override lateinit var contactRepository: ContactRepository
    override lateinit var messageRepository: MessageRepository
    override lateinit var preference: Preference
//    override lateinit var signalHandler: SignalServiceHandler
    override lateinit var activityProvider: ActivityProvider
    override lateinit var statisticCollector: StatisticCollector
    override lateinit var mediaButtonHandler: MediaButtonHandler
    override lateinit var storage: Storage
    override lateinit var objectMapper: ObjectMapper
    override lateinit var appApi: AppApi
    override lateinit var signalBroker: SignalBroker
    override val appServerEndpoint = BuildConfig.APP_SERVER_ENDPOINT
    override val gson: Gson = Gson()

    override val appService: AppService by lazy {
        Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl(appServerEndpoint)
                .client(httpClient)
                .build()
                .create(AppService::class.java)
    }

    override fun onCreate() {
        instance = this
        super.onCreate()

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)

        RxAndroidPlugins.getInstance().registerSchedulersHook(object : RxAndroidSchedulersHook() {
            private val scheduler = ImmediateMainThreadScheduler()

            override fun getMainThreadScheduler(): Scheduler {
                return scheduler
            }
        })

        if (BuildConfig.DEBUG.not()) {
            Fabric.with(this, Crashlytics(), Answers())
        }
        else {
            Fabric.with(this, Answers())
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? ch.qos.logback.classic.Logger)?.level = Level.ALL
        }

        MDC.put("pid", Process.myPid().toString())
        MDC.put("version", "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}")

        AndroidThreeTen.init(this)

        storage = Storage(this, this)
        objectMapper = ObjectMapper().apply {
            registerModule(JsonOrgModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }

        preference = AppPreference(this, PreferenceManager.getDefaultSharedPreferences(this), this)

        appApi = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(io.reactivex.schedulers.Schedulers.io()))
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .baseUrl(BuildConfig.APP_SERVER_ENDPOINT)
                .build()
                .create(AppApi::class.java)
        signalBroker = SignalBroker(this, this)

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
        messageRepository = MessageRepository(MessageSQLiteStorage(helper), PublishSubject.create<Unit>())

//        signalHandler = SignalServiceHandler(this, this)

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
        LocationHandler(this, signalHandler)
        statisticCollector = StatisticCollector(signalHandler)
        SDKInitializer.initialize(this)
    }

    open protected fun onBuildHttpClient(): OkHttpClient.Builder {
        return OkHttpClient.Builder().cache(Cache(cacheDir, Constants.HTTP_MAX_CACHE_SIZE))
    }

    class AppGlideModule : GlideModule {
        override fun applyOptions(p0: Context?, p1: GlideBuilder?) {
        }

        override fun registerComponents(context: Context, glide: Glide) {
            glide.register(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory((context.applicationContext as AppComponent).httpClient))
        }
    }

    companion object {
        @JvmStatic lateinit var instance : App
        private set
    }
}
