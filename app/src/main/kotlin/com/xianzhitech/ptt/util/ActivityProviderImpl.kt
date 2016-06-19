package com.xianzhitech.ptt.util

import android.app.Activity
import android.os.Bundle
import com.xianzhitech.ptt.ui.ActivityProvider

class ActivityProviderImpl : ActivityProvider, SimpleActivityLifecycleCallbacks() {

    override var currentStartedActivity: Activity? = null

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        currentStartedActivity = activity
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        super.onActivitySaveInstanceState(activity, outState)
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        if (currentStartedActivity == activity) {
            currentStartedActivity = null
        }
    }
}