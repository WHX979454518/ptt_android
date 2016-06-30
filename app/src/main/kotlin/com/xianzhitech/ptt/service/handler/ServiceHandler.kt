package com.xianzhitech.ptt.service.handler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.repo.getInRoomDescription
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import rx.Subscription

/**
 * 用于保证前台服务的Android Service
 */
class Service : android.app.Service() {
    private var subscription: Subscription? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val signalService = appComponent.signalHandler

        subscription = Observable.combineLatest(
                signalService.roomStatus.doOnNext {
                    logd("Room status changed to $it")
                },
                signalService.currentRoomIdSubject.switchMap { appComponent.roomRepository.getRoom(it).observe() },
                signalService.currentRoomIdSubject.switchMap { appComponent.roomRepository.getRoomName(it, excludeUserIds = arrayOf(signalService.currentUserId)).observe() },
                signalService.loginStatus,
                signalService.currentUserIdSubject.switchMap { appComponent.userRepository.getUser(it).observe() },
                getConnectivity(),
                { roomStatus, currRoom, roomName, loginStatus, currUser, connectivity -> State(roomStatus, currRoom, roomName, loginStatus, currUser, connectivity) })
                .subscribeSimple { onStateChanged(it) }

        receiveBroadcasts(false, Intent.ACTION_SCREEN_ON)
            .subscribe {
                val roomState = signalService.peekRoomState()
                if (roomState.currentRoomId != null && roomState.status != RoomStatus.IDLE) {
                    startActivity(Intent(this, RoomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    }

    override fun onDestroy() {
        subscription?.unsubscribe()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun onStateChanged(state: State) {
        logd("State changed to $state")
        val builder = NotificationCompat.Builder(this)
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setContentTitle(R.string.app_name.toFormattedString(this))
        val icon: Int

        if (state.roomStatus.inRoom) {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, RoomActivity::class.java), 0))
        } else {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0))
        }

        when (state.loginStatus) {
            LoginStatus.LOGGED_IN -> {
                when (state.roomStatus) {
                    RoomStatus.IDLE -> {
                        builder.setContentText(R.string.notification_user_online.toFormattedString(this, state.currUser?.name ?: ""))
                        icon = R.drawable.ic_notification_logged_on
                    }

                    RoomStatus.JOINING -> {
                        builder.setContentText(R.string.notification_joining_room.toFormattedString(this))
                        icon = R.drawable.ic_notification_joined_room
                    }

                    else -> {
                        builder.setContentText(state.currRoomName.getInRoomDescription(this))
                        icon = R.drawable.ic_notification_joined_room
                    }
                }
            }

            LoginStatus.LOGIN_IN_PROGRESS -> {
                if (state.connectivity.not()) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser?.name ?: ""))
                } else if (state.roomStatus == RoomStatus.IDLE) {
                    builder.setContentText(R.string.notification_user_logging_in.toFormattedString(this))
                } else {
                    builder.setContentText(R.string.notification_rejoining_room.toFormattedString(this))
                }

                icon = R.drawable.ic_notification_logged_on
            }

            LoginStatus.OFFLINE -> {
                builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser?.name ?: ""))
                icon = R.drawable.ic_notification_offline
            }

            LoginStatus.IDLE -> {
                if (state.currUser == null) {
                    stopForeground(true)
                } else if (state.connectivity.not()) {
                    builder.setContentText(R.string.notification_user_offline.toFormattedString(this, state.currUser.name))
                }
                return
            }
        }

        builder.setSmallIcon(icon)
        startForeground(R.id.notification_main, builder.build())
    }

    private data class State(val roomStatus: RoomStatus,
                             val currRoom: Room?,
                             val currRoomName: RoomName?,
                             val loginStatus: LoginStatus,
                             val currUser: User?,
                             val connectivity: Boolean)
}

class ServiceHandler(private val appContext: Context,
                     private val signalService: SignalServiceHandler) {
    init {
        signalService.loginStatus
                .subscribeSimple {
                    if (it != LoginStatus.IDLE) {
                        appContext.startService(Intent(appContext, Service::class.java))
                    } else {
                        appContext.stopService(Intent(appContext, Service::class.java))
                    }
                }
    }
}