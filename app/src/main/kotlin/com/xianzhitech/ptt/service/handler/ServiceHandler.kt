package com.xianzhitech.ptt.service.handler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.repo.getInRoomDescription
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.PushService
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.UserObject
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers


private val logger = LoggerFactory.getLogger("Service")

private data class State(val roomStatus: RoomStatus,
                         val currRoom: Room?,
                         val currRoomName: RoomName?,
                         val loginStatus: LoginStatus,
                         val currUser: User?,
                         val connectivity: Boolean)

class ServiceHandler(private val appContext: Context,
                     private val appComponent: AppComponent) {
    init {
        val signalService = appComponent.signalHandler
        signalService.loginState
                .filter { it.status == LoginStatus.LOGGED_IN }
                .subscribeSimple {
                    val currUser = it.currentUser as UserObject
                    PushService.start(appContext, currUser.pushServerUri, currUser.id, currUser.serviceToken)
                }

        appComponent.preference.userSessionTokenSubject
                .map { it != null }
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .switchMap { userHasLoggedIn ->
                    if (userHasLoggedIn.not()) {
                        logger.i { "User has logged out, stopping PushService" }
                        PushService.stop(appContext)
                        Observable.never<State>()
                    }
                    else {
                        logger.i { "User has logged in/logging in. Start monitoring events." }
                        Observable.combineLatest(
                                signalService.roomStatus,
                                signalService.currentRoomIdSubject.switchMap { appComponent.roomRepository.getRoom(it).observe() },
                                signalService.currentRoomIdSubject.switchMap { appComponent.roomRepository.getRoomName(it, excludeUserIds = arrayOf(appComponent.preference.userSessionToken?.userId)).observe() },
                                signalService.loginStatus,
                                appComponent.preference.userSessionTokenSubject.switchMap { appComponent.userRepository.getUser(it?.userId).observe() },
                                appContext.getConnectivity(),
                                { roomStatus, currRoom, roomName, loginStatus, currUser, connectivity -> State(roomStatus, currRoom, roomName, loginStatus, currUser, connectivity) })
                    }
                }
                .subscribeSimple { state : State ->
                    onStateChanged(state)
                }



        appContext.receiveBroadcasts(false, Intent.ACTION_SCREEN_ON)
                .subscribe {
                    val roomState = signalService.peekRoomState()
                    if (roomState.currentRoomId != null && roomState.status != RoomStatus.IDLE) {
                        logger.i { "Screen turning on and current in room. Open room activity." }
                        appContext.startActivity(Intent(appContext, RoomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
    }

    private fun onStateChanged(state: State) {
        logger.d { "State changed to $state" }

        if (appComponent.preference.userSessionToken == null) {
            logger.w { "User session has ended. Stopping service" }
            return
        }

        val builder = NotificationCompat.Builder(appContext)
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setContentTitle(R.string.app_name.toFormattedString(appContext))
        val icon: Int

        if (state.roomStatus.inRoom) {
            builder.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, RoomActivity::class.java), 0))
        } else {
            builder.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), 0))
        }

        when (state.loginStatus) {
            LoginStatus.LOGGED_IN -> {
                when (state.roomStatus) {
                    RoomStatus.IDLE -> {
                        builder.setContentText(R.string.notification_user_online.toFormattedString(appContext, state.currUser?.name ?: ""))
                        icon = R.drawable.ic_notification_logged_on
                    }

                    RoomStatus.JOINING -> {
                        builder.setContentText(R.string.notification_joining_room.toFormattedString(appContext))
                        icon = R.drawable.ic_notification_joined_room
                    }

                    else -> {
                        builder.setContentText(state.currRoomName.getInRoomDescription(appContext))
                        icon = R.drawable.ic_notification_joined_room
                    }
                }
            }

            LoginStatus.LOGIN_IN_PROGRESS -> {
                if (state.connectivity.not()) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(appContext, state.currUser?.name ?: ""))
                } else if (state.roomStatus == RoomStatus.IDLE) {
                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(appContext))
                } else {
                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(appContext))
                }

                icon = R.drawable.ic_notification_logged_on
            }

            LoginStatus.OFFLINE -> {
                builder.setContentText(R.string.notification_user_offline.toFormattedString(appContext, state.currUser?.name ?: ""))
                icon = R.drawable.ic_notification_offline
            }

            LoginStatus.IDLE -> {
                return
            }
        }

        builder.setSmallIcon(icon)
        PushService.update(appContext, builder.build())
    }

}