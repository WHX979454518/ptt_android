package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.PushService
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.RoomInvitationObject
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.json.JSONObject
import org.slf4j.LoggerFactory
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import java.io.Serializable
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("RoomInvitationHandler")

class RoomInvitationHandler() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val invite : RoomInvitation = when (intent?.action) {
            SignalServiceHandler.ACTION_ROOM_INVITATION -> intent!!.getSerializableExtra(SignalServiceHandler.EXTRA_INVITATION) as RoomInvitation
            PushService.ACTION_MESSAGE -> RoomInvitationObject(JSONObject(intent!!.getStringExtra(PushService.EXTRA_MSG)))
            else -> return
        }

        val appComponent = context.appComponent
        Single.zip(
                appComponent.userRepository.getUser(invite.inviterId).getAsync(),
                appComponent.roomRepository.getRoom(appComponent.signalHandler.currentRoomId).getAsync(),
                if (invite is RoomInvitationObject) {
                    appComponent.roomRepository.saveRooms(listOf(invite.room))
                            .execAsync()
                            .onErrorComplete()
                            .toSingleDefault(Unit)
                } else {
                    Single.just(Unit)
                },
                { user, room, ignored -> InviteInfo(invite, room, user) })
                .flatMap { info ->
                    // 看看用户是否在登陆，如果是，则需要等待登陆完成
                    when (appComponent.signalHandler.peekLoginState().status) {
                        LoginStatus.LOGGED_IN -> Single.just(info)
                        else -> {
                            logger.i { "Waiting for user to log in..." }
                            appComponent.signalHandler.loginStatus
                                    .first { it == LoginStatus.LOGGED_IN }
                                    .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                                    .toSingle()
                                    .map { info }
                        }
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeSimple { onReceive(context, it.invitation, it.currentRoom, it.inviter) }
    }

    private fun onReceive(context: Context, invitation: RoomInvitation, currentRoom: RoomModel?, inviter: User?) {
        if (invitation.roomId == currentRoom?.id) {
            logger.i { "Already in room ${invitation.roomId}. Skip inviting..." }
            return
        }

        logger.d { "Receive room invitation $invitation, currRoom: $currentRoom, inviter: $inviter" }

        val intent = Intent(context, RoomActivity::class.java)

        if (currentRoom == null ||
                System.currentTimeMillis() - currentRoom.lastActiveTime.time >= Constants.ROOM_IDLE_TIME_SECONDS * 1000L ||
                (inviter != null && inviter.priority == 0)) {
            logger.i { "Join room ${invitation.roomId} directly" }
            // 如果满足下列条件之一, 则直接接受邀请并进入对讲房间
            //  1. 当前没有对讲房间
            //  2. 上一次房间有动作的时刻已经很久远
            //  3. 邀请者是拥有最高权限
            intent.putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, invitation.roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true)
        } else {
            logger.i { "Sending out invitation" }
            intent.putExtra(RoomActivity.EXTRA_INVITATIONS, listOf(invitation) as Serializable)
        }

        val startedActivity = context.appComponent.activityProvider.currentStartedActivity

        if (startedActivity != null) {
            startedActivity.startActivityWithAnimation(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private data class InviteInfo(val invitation: RoomInvitation,
                                  val currentRoom: RoomModel?,
                                  val inviter: User?)
}