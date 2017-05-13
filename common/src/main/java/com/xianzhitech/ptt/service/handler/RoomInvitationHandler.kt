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
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.w
import com.xianzhitech.ptt.ext.without
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.util.isDownTime
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory
import org.threeten.bp.LocalTime


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

        val startedActivity = appComponent.activityProvider.currentStartedActivity as? BaseActivity

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
            if (startedActivity == null) {
                context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, invitation.room.id)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_FROM_INVITATION, invitation.room.id)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_IS_VIDEO_CHAT, false)
                )
            }
            else {
                startedActivity.joinRoomConfirmed(invitation.room.id, true, false)
            }
        } else {
            logger.i { "Sending out invitation" }

            if (startedActivity == null) {
                context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)
                        .putParcelableArrayListExtra(BaseActivity.EXTRA_PENDING_INVITATION, arrayListOf(invitation))
                )
            }
            else {
                startedActivity.onNewPendingInvitation(listOf(invitation))
            }
        }

    }

    companion object {
        private val logger = LoggerFactory.getLogger("RoomInvitationHandler")
    }
}