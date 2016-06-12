package com.xianzhitech.ptt.util

import android.app.Activity
import android.os.Bundle
import com.trello.rxlifecycle.ActivityEvent
import com.xianzhitech.ptt.ui.ActivityProvider
import rx.subjects.PublishSubject

class ActivityProviderImpl : ActivityProvider, SimpleActivityLifecycleCallbacks() {

    override var currentStartedActivity: Activity? = null
    override val activityEventSubject = PublishSubject.create<ActivityEvent>()

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        activityEventSubject.onNext(ActivityEvent.PAUSE)
    }

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        activityEventSubject.onNext(ActivityEvent.START)
        currentStartedActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        super.onActivityDestroyed(activity)
        activityEventSubject.onNext(ActivityEvent.DESTROY)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        super.onActivitySaveInstanceState(activity, outState)
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        activityEventSubject.onNext(ActivityEvent.STOP)
        if (currentStartedActivity == activity) {
            currentStartedActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        activityEventSubject.onNext(ActivityEvent.CREATE)
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        activityEventSubject.onNext(ActivityEvent.RESUME)
    }
}