package com.xianzhitech.ptt.room

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.xianzhitech.ptt.ext.registerLocalBroadcastReceiver
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.InviteToJoin
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity

class InviteToJoinBroadcastReceiver(private val app: Application,
                                    private val roomRepository: RoomRepository,
                                    private val userRepository: UserRepository,
                                    private val signalService: SignalService) : BroadcastReceiver() {

    private var currActivity : BaseActivity? = null

    init {
        app.registerLocalBroadcastReceiver(this, IntentFilter().apply { addAction(SignalService.ACTION_INVITE_TO_JOIN) })
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity?) {
                if (activity is BaseActivity) {
                    currActivity = activity
                }
            }

            override fun onActivityStopped(activity: Activity?) {
                if (currActivity == activity) {
                    currActivity = null
                }
            }

            override fun onActivityPaused(activity: Activity?) { }
            override fun onActivityDestroyed(activity: Activity?) { }
            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { }
            override fun onActivityResumed(activity: Activity?) { }
        })
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == SignalService.ACTION_INVITE_TO_JOIN) {
            val inviteToJoin = intent!!.getSerializableExtra(SignalService.EXTRA_INVITE) as InviteToJoin
            currActivity?.onInviteToJoin(inviteToJoin) ?: app.startActivity(Intent(app, RoomActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BaseActivity.EXTRA_INVITE_TO_JOIN, inviteToJoin))
        }
    }
}