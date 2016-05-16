package com.xianzhitech.ptt.ui.service

import android.app.PendingIntent
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.getConnectivity
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import rx.Subscription
import java.util.concurrent.TimeUnit

/**
 * 用于保证前台服务的Android Service
 */
class Service : android.app.Service() {
    private var subscription : Subscription? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val appComponent = application as AppComponent
        val signalService = appComponent.signalService

        subscription = Observable.combineLatest(
                signalService.roomState,
                signalService.roomState.distinctUntilChanged { it.currentRoomID }.flatMap {
                    appComponent.roomRepository.getRoom(it.currentRoomID).observe()
                        .combineWith(appComponent.roomRepository.getRoomName(it.currentRoomID).observe())
                },
                signalService.loginState,
                signalService.loginState.distinctUntilChanged { it.currentUserID }.flatMap { appComponent.userRepository.getUser(it.currentUserID).observe() },
                getConnectivity(),
                { roomState, currRoom, loginState, currUser, connectivity -> State(roomState, currRoom.first, currRoom.second, loginState, currUser, connectivity) })
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribeSimple { onStateChanged(it) }
    }

    override fun onDestroy() {
        subscription?.unsubscribe()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    internal fun onStateChanged(state : State) {
        logd("State changed to $state")
        val builder = NotificationCompat.Builder(this)
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setContentTitle(R.string.app_name.toFormattedString(this))
        val icon : Int

        when (state.loginState.status) {
            LoginStatus.LOGGED_IN -> {
                when (state.roomState.status) {
                    RoomStatus.IDLE -> {
                        builder.setContentText(R.string.notification_user_online.toFormattedString(this, state.currUser?.name ?: ""))
                        builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0))
                        icon = R.drawable.ic_notification_logged_on
                    }

                    RoomStatus.JOINING -> {
                        builder.setContentText(R.string.notification_joining_room.toFormattedString(this))
                        builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, RoomActivity::class.java), 0))
                        icon = R.drawable.ic_notification_joined_room
                    }

                    else -> {
                        builder.setContentText(R.string.notification_joined_room.toFormattedString(this, state.currRoomName))
                        builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, RoomActivity::class.java), 0))
                        icon = R.drawable.ic_notification_joined_room
                    }
                }
            }

            LoginStatus.LOGIN_IN_PROGRESS -> {
                if (state.connectivity.not()) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser?.name ?: ""))
                } else if (state.roomState.status == RoomStatus.IDLE) {
                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(this))
                } else {
                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(this))
                }

                icon = R.drawable.ic_notification_logged_on
            }

            LoginStatus.OFFLINE -> {
                builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser?.name ?: ""))
                builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0))
                icon = R.drawable.ic_notification_offline
            }

            LoginStatus.IDLE -> {
                if (state.loginState.currentUserID == null) {
                    stopForeground(true)
                } else if (state.connectivity.not() && state.currUser != null) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser.name))
                }
                return
            }
        }

        builder.setSmallIcon(icon)
        startForeground(R.id.notification_main, builder.build())
    }

    internal data class State(val roomState: RoomState,
                              val currRoom: Room?,
                              val currRoomName: String?,
                              val loginState: LoginState,
                              val currUser : User?,
                              val connectivity : Boolean)
}