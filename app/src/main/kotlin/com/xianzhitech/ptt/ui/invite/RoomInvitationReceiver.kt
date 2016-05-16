package com.xianzhitech.ptt.ui.invite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.registerLocalBroadcastReceiver
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity

class RoomInvitationReceiver(private val appContext: Context,
                             private val signalService: SignalService,
                             private val activityProvider: ActivityProvider) : BroadcastReceiver() {
    init {
        appContext.registerLocalBroadcastReceiver(this, IntentFilter().apply { addAction(SignalService.ACTION_INVITE_TO_JOIN) })
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == SignalService.ACTION_INVITE_TO_JOIN) {
            val invite = intent!!.getSerializableExtra(SignalService.EXTRA_INVITE) as RoomInvitation
            val roomStatus = signalService.peekRoomState().status
            val startedActivity = activityProvider.currentStartedActivity

            if (roomStatus == RoomStatus.IDLE || invite.inviterId == Constants.ADMIN_USER_ID) {
                // 当前没有对讲会话, 或者最高权限用户拉起的会话, 直接响应
                (startedActivity as? BaseActivity)?.joinRoomConfirmed(invite.roomId) ?:
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