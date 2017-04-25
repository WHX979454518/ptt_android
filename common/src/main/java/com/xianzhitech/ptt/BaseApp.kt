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
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.glide.GlideImageLoader
import com.github.piasy.biv.view.BigImageView
import com.jakewharton.threetenabp.AndroidThreeTen
import com.xianzhitech.ptt.api.AppApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Storage
import com.xianzhitech.ptt.media.AudioHandler
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.service.handler.LocationHandler
import com.xianzhitech.ptt.service.handler.MessageSync
import com.xianzhitech.ptt.service.handler.RoomAutoQuitHandler
import com.xianzhitech.ptt.service.handler.ServiceHandler
import com.xianzhitech.ptt.service.handler.StatisticCollector
import com.xianzhitech.ptt.service.handler.WakeLockHandler
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
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.InputStream


abstract class BaseApp : MultiDexApplication(), AppComponent {

    override val httpClient : OkHttpClient by lazy { onBuildHttpClient().build() }
    override lateinit var preference: Preference
    override lateinit var activityProvider: ActivityProvider
    override lateinit var statisticCollector: StatisticCollector
    override lateinit var mediaButtonHandler: MediaButtonHandler
    override lateinit var storage: Storage
    override lateinit var objectMapper: ObjectMapper
    override lateinit var appApi: AppApi
    override lateinit var signalBroker: SignalBroker

    override val appServerEndpoint
    get() = BuildConfig.APP_SERVER_ENDPOINT

    override fun onCreate() {
        instance = this
        super.onCreate()

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)

        if (BuildConfig.DEBUG.not()) {
            Fabric.with(this, Crashlytics())
        }
        else {
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? ch.qos.logback.classic.Logger)?.level = Level.ALL
        }

        MDC.put("pid", Process.myPid().toString())
        MDC.put("version", "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}")

        AndroidThreeTen.init(this)

        storage = Storage(this, this)
        objectMapper = ObjectMapper().apply {
            registerModule(JsonOrgModule())
            registerModule(KotlinModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }

        preference = AppPreference(this, PreferenceManager.getDefaultSharedPreferences(this), this)

        appApi = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(io.reactivex.schedulers.Schedulers.io()))
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .baseUrl(appServerEndpoint)
                .build()
                .create(AppApi::class.java)
        signalBroker = SignalBroker(this, this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }

        activityProvider = ActivityProviderImpl().apply {
            registerActivityLifecycleCallbacks(this)
        }

        mediaButtonHandler = MediaButtonHandler(signalBroker)
        AudioHandler(this, signalBroker, mediaButtonHandler, httpClient, preference)
        ServiceHandler(this, this)
        RoomAutoQuitHandler(preference, activityProvider, signalBroker)
        LocationHandler(this, signalBroker)
        statisticCollector = StatisticCollector(signalBroker)
        WakeLockHandler(signalBroker, this)
        MessageSync(this)
        SDKInitializer.initialize(this)

        BigImageViewer.initialize(GlideImageLoader.with(this, httpClient))
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
        @JvmStatic lateinit var instance : BaseApp
        private set
    }
}
