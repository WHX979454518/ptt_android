package com.xianzhitech.ptt

import android.app.Activity
import android.os.Bundle
import com.android.debug.hv.ViewServer
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import okhttp3.OkHttpClient

class DevApp : App() {

    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)

        registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ViewServer.get(activity).addWindow(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                ViewServer.get(activity).setFocusedWindow(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                ViewServer.get(activity).removeWindow(activity)
            }
        })
    }

    override fun onBuildHttpClient(): OkHttpClient.Builder {
        return super.onBuildHttpClient().addNetworkInterceptor(StethoInterceptor())
    }
}
