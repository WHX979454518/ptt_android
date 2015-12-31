package com.xianzhitech.ptt

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.android.debug.hv.ViewServer
import com.facebook.stetho.Stetho

/**
 * Created by fanchao on 7/12/15.
 */
class DevApp : App() {

    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ViewServer.get(activity).addWindow(activity)
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                ViewServer.get(activity).setFocusedWindow(activity)
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
                ViewServer.get(activity).removeWindow(activity)
            }
        })

    }

    //    override val authProvider by lazy { DevProvider(broker) }
    //    override val signalProvider by lazy { authProvider }
    //    override val talkEngineProvider = object : TalkEngineProvider {
    //        override fun createEngine() = DevTalkEngine()
    //    }
}