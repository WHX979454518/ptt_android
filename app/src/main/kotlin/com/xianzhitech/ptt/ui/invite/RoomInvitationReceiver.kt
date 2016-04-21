package com.xianzhitech.ptt.ui.invite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.registerLocalBroadcastReceiver
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity

class RoomInvitationReceiver(private val appContext: Context,
                             private val activityProvider: ActivityProvider) : BroadcastReceiver() {


    init {
        appContext.registerLocalBroadcastReceiver(this, IntentFilter().apply { addAction(SignalService.ACTION_INVITE_TO_JOIN) })
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == SignalService.ACTION_INVITE_TO_JOIN) {
            val invite = intent!!.getSerializableExtra(SignalService.EXTRA_INVITE) as RoomInvitation
            val startedActivity = activityProvider.currentStartedActivity
            if (invite.inviterId == Constants.ADMIN_USER_ID) {
                // 最高权限用户拉起的群, 直接响应
                (startedActivity as? BaseActivity)?.joinRoom(invite.roomId, confirmed = true) ?:
                        BaseActivity.startActivityJoiningRoom(context, RoomActivity::class.java, invite.roomId)
            }
            else if (startedActivity != null) {
                startedActivity.startActivity(Intent(startedActivity, RoomInvitationActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                        .putExtra(RoomInvitationActivity.EXTRA_NEW_INVITE, invite))
            }
            else {
                context.startActivity(Intent(context, RoomInvitationActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(RoomInvitationActivity.EXTRA_NEW_INVITE, invite))
            }
        }
    }
}