package com.xianzhitech.ptt.ui

import android.app.Activity
import com.trello.rxlifecycle.ActivityEvent
import rx.Observable

interface ActivityProvider {
    val currentStartedActivity : Activity?
    val activityEventSubject : Observable<ActivityEvent>
}