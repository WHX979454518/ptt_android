package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import java.io.Serializable

class RoomInvitationHandler(private val appContext: Context,
                            private val signalService: SignalServiceHandler,
                            private val userRepository: UserRepository,
                            private val roomRepository: RoomRepository,
                            private val activityProvider: ActivityProvider) : BroadcastReceiver() {
    init {
        appContext.registerLocalBroadcastReceiver(this, IntentFilter(SignalServiceHandler.ACTION_ROOM_INVITATION))
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == SignalServiceHandler.ACTION_ROOM_INVITATION) {
            val invite: RoomInvitation = intent!!.getSerializableExtra(SignalServiceHandler.EXTRA_INVITATION) as RoomInvitation
            Observable.combineLatest(
                    userRepository.getUser(invite.inviterId).getAsync().toObservable(),
                    roomRepository.getRoom(signalService.currentRoomId).getAsync().toObservable(),
                    { user, room -> InviteInfo(invite, room, user) }
            )
                    .observeOnMainThread()
                    .subscribeSimple { onReceive(it.invitation, it.currentRoom, it.inviter) }
        }
    }

    private fun onReceive(invitation: RoomInvitation, currentRoom: RoomModel?, inviter: User?) {
        if (invitation.roomId == currentRoom?.id) {
            logd("Already in room ${invitation.roomId}. Skip inviting...")
            return
        }

        val intent = Intent(appContext, RoomActivity::class.java)

        if (currentRoom == null ||
                System.currentTimeMillis() - currentRoom.lastActiveTime.time >= Constants.ROOM_IDLE_TIME_SECONDS * 1000L ||
                (inviter != null && inviter.priority == 0)) {
            // 如果满足下列条件之一, 则直接接受邀请并进入对讲房间
            //  1. 当前没有对讲房间
            //  2. 上一次房间有动作的时刻已经很久远
            //  3. 邀请者是拥有最高权限
            intent.putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, invitation.roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true)
        } else {
            intent.putExtra(RoomActivity.EXTRA_INVITATIONS, listOf(invitation) as Serializable)
        }

        val startedActivity = activityProvider.currentStartedActivity

        if (startedActivity != null) {
            startedActivity.startActivityWithAnimation(intent)
        } else {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private data class InviteInfo(val invitation: RoomInvitation,
                                  val currentRoom: RoomModel?,
                                  val inviter: User?)
}