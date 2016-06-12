package com.xianzhitech.ptt

import android.app.Activity
import android.os.Bundle
import com.android.debug.hv.ViewServer
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.xianzhitech.ptt.service.AppParams
import com.xianzhitech.ptt.service.AppService
import com.xianzhitech.ptt.service.Feedback
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import okhttp3.OkHttpClient
import rx.Completable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class DevApp : App() {

    override val appService: AppService
        get() = object : AppService {
            override fun retrieveAppParams(appVersion: Int, versionName: String, deviceName: String, modelName: String, deviceId: String): Single<AppParams> {
                return Single.just(AppParams(null, false, null, "http://netptt.cn:20000"))
            }

            override fun submitFeedback(feedback: Feedback): Completable {
                return Completable.timer(5, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            }
        }

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
