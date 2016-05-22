package com.xianzhitech.ptt.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.subscribeSimple
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
        (application as AppComponent).signalService.roomState
                .doOnNext { logd("Current online members are: ${it.onlineMemberIds}") }
                .distinctUntilChanged { it.onlineMemberIds }
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

    private fun onRoomStateChanged(roomState: RoomState) {
        if (lastRoomState?.let { it.currentRoomId == roomState.currentRoomId && it.onlineMemberIds.size > 1 } ?: false &&
                roomState.status.inRoom &&
                roomState.onlineMemberIds.size <= 1 &&
                (application as AppComponent).preference.autoExit) {

            application.signalService.leaveRoom().subscribeSimple()
            (currActiveActivity?.get() as? RoomActivity)?.finish()
        }

        lastRoomState = roomState
    }
}