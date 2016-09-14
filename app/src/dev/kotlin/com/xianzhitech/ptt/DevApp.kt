package com.xianzhitech.ptt

import android.app.Activity
import android.os.Handler
import android.preference.PreferenceManager
import com.xianzhitech.ptt.maintain.service.AppService
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.schedulers.Schedulers

class DevApp : App() {
    companion object {
        private const val KEY_APP_SERVER = "test_app_server"
    }

    override val appService: AppService
        get() = Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(appServerEndpoint)
                .client(httpClient)
                .build()
                .create(AppService::class.java)

    override var appServerEndpoint: String
        get() = PreferenceManager.getDefaultSharedPreferences(this).getString(KEY_APP_SERVER, BuildConfig.APP_SERVER_ENDPOINT)
        set(value) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(KEY_APP_SERVER, value)
                    .apply()
        }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {
            private val handler = Handler()

            override fun onActivityStarted(activity: Activity) {
                handler.postDelayed({
                }, 1000)
            }
        })
    }

}
