package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.common.base.Optional
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.data.RoomInfo
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.util.isDownTime
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory
import org.threeten.bp.LocalTime
import java.io.Serializable


class RoomInvitationHandler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val invite: WalkieRoomInvitationEvent = intent?.getParcelableExtra<WalkieRoomInvitationEvent>(SignalBroker.EXTRA_EVENT) ?: return


        val appComponent = context.appComponent
        val currentRoomId = appComponent.signalBroker.peekWalkieRoomId()

        val roomInfo : Single<Optional<RoomInfo>>

        if (currentRoomId == null) {
            roomInfo = Single.just(Optional.absent())
        } else {
            roomInfo = appComponent.storage.getRoomInfo(currentRoomId).firstOrError()
        }

        appComponent.storage.saveRoom(invite.room)
                .flatMap { roomInfo }
                .toMaybe()
                .logErrorAndForget()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { onReceive(context, invite, currentRoomId, it.orNull()) }
    }

    private fun onReceive(context: Context, invitation: WalkieRoomInvitationEvent, currentRoomId : String?, currentRoomInfo: RoomInfo?) {
        if (invitation.room.id == currentRoomId) {
            logger.i { "Already in room ${invitation.room.id}. Skip inviting..." }
            return
        }

        val appComponent = context.appComponent
        val user = appComponent.signalBroker.currentUser.value.orNull()
        if (user == null) {
            logger.e { "No user logged in" }
            return
        }

        if (user.hasPermission(Permission.MUTE) &&
                appComponent.preference.enableDownTime &&
                LocalTime.now().isDownTime(
                        appComponent.preference.downTimeStart,
                        appComponent.preference.downTimeEnd)) {
            logger.i { "ContactUser in downtime, skip inviting..." }
            return
        }

        if (user.hasPermission(Permission.RECEIVE_INDIVIDUAL_CALL).not() &&
                invitation.room.groupIds.isEmpty() &&
                invitation.room.extraMemberIds.without(user.id).size == 1) {
            logger.w { "ContactUser has no permission to receive individual call" }
            return
        }

        if (user.hasPermission(Permission.RECEIVE_TEMP_GROUP_CALL).not() &&
                invitation.room.groupIds.isEmpty() &&
                invitation.room.extraMemberIds.without(user.id).size > 1) {
            logger.w { "ContactUser has no permission to receive temporary group call" }
            return
        }

        logger.d { "Receive room invitation $invitation, currRoomId: $currentRoomId, currRoomInfo: $currentRoomInfo" }

        val intent = Intent(context, RoomActivity::class.java)

        if (currentRoomInfo == null ||
                currentRoomInfo.lastWalkieActiveTime == null ||
                System.currentTimeMillis() - currentRoomInfo.lastWalkieActiveTime!!.time >= Constants.ROOM_IDLE_TIME_SECONDS * 1000L ||
                (invitation.inviterPriority == 0) ||
                invitation.force) {
            logger.i { "Join room ${invitation.room.id} directly" }
            // 如果满足下列条件之一, 则直接接受邀请并进入对讲房间
            //  1. 当前没有对讲房间
            //  2. 上一次房间有动作的时刻已经很久远
            //  3. 邀请者是拥有最高权限
            intent.putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, invitation.room.id)
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

    companion object {
        private val logger = LoggerFactory.getLogger("RoomInvitationHandler")
    }
}