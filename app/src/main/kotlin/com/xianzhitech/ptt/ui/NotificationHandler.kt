package com.xianzhitech.ptt.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.getConnectivity
import com.xianzhitech.ptt.ext.getRoomName
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomWithMembers
import com.xianzhitech.ptt.repo.optRoomWithMembers
import com.xianzhitech.ptt.repo.optUser
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.sio.SocketIOBackgroundService
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import java.util.concurrent.TimeUnit


/**
 * 处理通知栏显示房间状态、处理点击事件的类，必须在[Application#onCreate]中创建
 */
class NotificationHandler(private val appContext: Context) {
    init {
        val appComponent = appContext.applicationContext as AppComponent
        (appComponent).connectToBackgroundService()
                .flatMap { service ->
                    Observable.combineLatest(
                            service.roomState,
                            service.roomState.distinct { it.currentRoomID }.flatMap { appComponent.roomRepository.optRoomWithMembers(it.currentRoomID) },
                            service.loginState,
                            service.loginState.distinct { it.currentUserID }.flatMap { appComponent.userRepository.optUser(it.currentUserID) },
                            appContext.getConnectivity(),
                            { roomState, currRoom, loginState, currUser, connectivity -> service to State(roomState, currRoom, loginState, currUser, connectivity) }
                    )
                }
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribe {
                    onStateChanged(it.first, it.second)
                }

    }

    internal fun onStateChanged(service : BackgroundServiceBinder, state : State) {
        logd("State changed to $state")
        val builder = NotificationCompat.Builder(appContext)
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setContentTitle(R.string.app_name.toFormattedString(appContext))
        val icon : Int

        when (state.loginState.status) {
            LoginState.Status.LOGGED_IN -> {
                when (state.roomState.status) {
                    RoomState.Status.IDLE -> {
                        builder.setContentText(R.string.notification_user_online.toFormattedString(appContext, state.currUser?.name ?: ""))
                        builder.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), 0))
                        icon = R.drawable.ic_notification_logged_on
                    }

                    RoomState.Status.JOINING -> {
                        builder.setContentText(R.string.notification_joining_room.toFormattedString(appContext))
                        builder.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, RoomActivity::class.java), 0))
                        icon = R.drawable.ic_notification_joined_room
                    }

                    else -> {
                        builder.setContentText(R.string.notification_joined_room.toFormattedString(appContext, state.currRoom?.getRoomName(appContext) ?: ""))
                        builder.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, RoomActivity::class.java), 0))
                        icon = R.drawable.ic_notification_joined_room
                    }
                }
            }

            LoginState.Status.LOGIN_IN_PROGRESS -> {
                if (state.connectivity.not()) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(appContext, state.currUser?.name ?: ""))
                } else if (state.roomState.status == RoomState.Status.IDLE) {
                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(appContext))
                } else {
                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(appContext))
                }

                icon = R.drawable.ic_notification_logged_on
            }

            LoginState.Status.OFFLINE -> {
                builder.setContentText(R.string.notification_user_offline.toFormattedString(appContext, state.currUser?.name ?: ""))
                icon = R.drawable.ic_notification_offline
            }

            LoginState.Status.IDLE -> {
                if (state.loginState.currentUserID == null) {
                    service.stopForeground(true)
                } else if (state.connectivity.not() && state.currUser != null) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(appContext, state.currUser.name))
                }
                return
            }
        }

        builder.setSmallIcon(icon)
        service.startForeground(SocketIOBackgroundService.SERVICE_NOTIFICATION_ID, builder.build())
    }

    internal data class State(val roomState: RoomState,
                              val currRoom: RoomWithMembers?,
                              val loginState: LoginState,
                              val currUser : User?,
                              val connectivity : Boolean)
}