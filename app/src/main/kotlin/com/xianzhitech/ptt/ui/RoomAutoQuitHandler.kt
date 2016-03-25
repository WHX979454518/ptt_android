package com.xianzhitech.ptt.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.preference.PreferenceManager
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.ui.room.RoomActivity
import java.lang.ref.WeakReference

/**
 * 处理自动退出房间的逻辑
 */
class RoomAutoQuitHandler(private val application: Application) {

    private var currActiveActivity : WeakReference<Activity>? = null
    private var lastRoomState : RoomState? = null

    init {
        (application as AppComponent).connectToBackgroundService()
                .flatMap { it.roomState }
                .distinct { it.currentRoomOnlineMemberIDs }
                .subscribe { onRoomStateChanged(it) }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                currActiveActivity = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (currActiveActivity?.get() == activity) {
                    currActiveActivity = null
                }
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityPaused(activity: Activity) {
            }
        })
    }

    internal fun onRoomStateChanged(roomState: RoomState) {
        if (lastRoomState?.currentRoomOnlineMemberIDs?.size ?: 0 > 1 &&
                roomState.status.inRoom &&
                roomState.currentRoomOnlineMemberIDs.size == 1 &&
                PreferenceManager.getDefaultSharedPreferences(application).getBoolean("auto_exit", true)) {

            (application as AppComponent).connectToBackgroundService()
                    .flatMap { it.requestQuitCurrentRoom() }
                    .subscribe(GlobalSubscriber())

            (currActiveActivity?.get() as? RoomActivity)?.finish()
        }

        lastRoomState = roomState
    }
}