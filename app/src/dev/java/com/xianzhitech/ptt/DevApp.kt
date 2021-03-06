package com.xianzhitech.ptt

import android.app.Activity
import android.os.Handler
import android.os.StrictMode
import android.preference.PreferenceManager
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.xianzhitech.ptt.app.BuildConfig
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import okhttp3.OkHttpClient

class DevApp : App() {
    companion object {
        private const val KEY_APP_SERVER = "test_app_server"
    }

    override val currentVersion: String
        get() = "dev"

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

        Stetho.initializeWithDefaults(this)
    }

    override fun onBuildHttpClient(): OkHttpClient.Builder {
        return super.onBuildHttpClient().addNetworkInterceptor(StethoInterceptor())
    }

}
