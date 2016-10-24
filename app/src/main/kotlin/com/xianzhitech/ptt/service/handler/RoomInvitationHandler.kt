package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.RoomInvitationObject
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.slf4j.LoggerFactory
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import java.io.Serializable

private val logger = LoggerFactory.getLogger("RoomInvitationHandler")

class RoomInvitationHandler() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val invite : RoomInvitation = when (intent?.action) {
            SignalServiceHandler.ACTION_ROOM_INVITATION -> intent!!.getSerializableExtra(SignalServiceHandler.EXTRA_INVITATION) as RoomInvitation
            else -> return
        }

        val appComponent = context.appComponent
        Single.zip(
                appComponent.roomRepository.getRoom(appComponent.signalHandler.peekCurrentRoomId()).getAsync(),
                if (invite is RoomInvitationObject) {
                    appComponent.roomRepository.saveRooms(listOf(invite.room))
                            .execAsync()
                            .onErrorComplete()
                            .toSingleDefault(Unit)
                } else {
                    Single.just(Unit)
                },
                { room, ignored -> RoomInvitationHandler.InviteInfo(invite, room) })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeSimple { onReceive(context, it.invitation, it.currentRoom) }
    }

    private fun onReceive(context: Context, invitation: RoomInvitation, currentRoom: RoomModel?) {
        if (invitation.roomId == currentRoom?.id) {
            logger.i { "Already in room ${invitation.roomId}. Skip inviting..." }
            return
        }

        val appComponent = context.appComponent
        if (appComponent.signalHandler.currentUserCache?.permissions?.contains(Permission.MUTE) ?: false &&
                appComponent.preference.enableDownTime &&
                appComponent.preference.downTime.isDownTime(System.currentTimeMillis())) {
            logger.i { "User in downtime, skip inviting..." }
            return
        }

        logger.d { "Receive room invitation $invitation, currRoom: $currentRoom" }

        val intent = Intent(context, RoomActivity::class.java)

        if (currentRoom == null ||
                System.currentTimeMillis() - currentRoom.lastActiveTime.time >= Constants.ROOM_IDLE_TIME_SECONDS * 1000L ||
                (invitation.inviterPriority == 0) ||
                invitation.force) {
            logger.i { "Join room ${invitation.roomId} directly" }
            // 如果满足下列条件之一, 则直接接受邀请并进入对讲房间
            //  1. 当前没有对讲房间
            //  2. 上一次房间有动作的时刻已经很久远
            //  3. 邀请者是拥有最高权限
            intent.putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, invitation.roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_FROM_INVITATION, true)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true)
        } else {
            logger.i { "Sending out invitation" }
            intent.putExtra(RoomActivity.EXTRA_INVITATIONS, listOf(invitation) as Serializable)
        }

        val startedActivity = appComponent.activityProvider.currentStartedActivity

        if (startedActivity != null) {
            startedActivity.startActivityWithAnimation(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private data class InviteInfo(val invitation: RoomInvitation,
                                  val currentRoom: RoomModel?)
}