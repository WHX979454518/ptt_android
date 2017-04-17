package com.xianzhitech.ptt.service.handler

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.google.common.base.Optional
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.call.CallActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function6
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("Service")

private data class State(val walkieRoomName: String?,
                         val videoRoomName: String?,
                         val connectivity: Boolean)

class BackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == BackgroundService.ACTION_UPDATE_NOTIFICATION) {
            startForeground(1, intent.getParcelableExtra(BackgroundService.EXTRA_NOTIFICATION))
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_UPDATE_NOTIFICATION = "update_notification"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

class ServiceHandler(private val appContext: Context,
                     private val appComponent: AppComponent) {
    init {
        val signalService = appComponent.signalBroker

        val currentWalkieRoomId = signalService.currentWalkieRoomId

        val currentWalkieRoomName = currentWalkieRoomId
                .switchMap {
                    if (it.isPresent) {
                        appComponent.storage.getRoomWithName(it.get())
                    } else {
                        Observable.just(Optional.absent())
                    }
                }

        val currentVideoRoomName = signalService.currentVideoRoomId
                .distinctUntilChanged()
                .switchMap {
                    if (it.isPresent) {
                        appComponent.storage.getRoomWithName(it.get())
                    } else {
                        Observable.just(Optional.absent())
                    }
                }

        signalService.currentUser
                .map { it.isPresent }
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .switchMap { loggedIn ->
                    if (loggedIn) {
                        logger.i { "ContactUser has logged in/logging in. Start monitoring events." }
                        Observable.combineLatest(
                                signalService.connectionState,
                                signalService.currentWalkieRoomState.distinctUntilChanged(RoomState::status),
                                signalService.currentUser,
                                currentWalkieRoomName,
                                currentVideoRoomName,
                                appContext.getConnectivityObservable(),

                                Function6 { _, _, _, walkieRoom, videoRoom, connectivity ->
                                    State(walkieRoomName = walkieRoom.orNull()?.second,
                                            videoRoomName = videoRoom.orNull()?.second,
                                            connectivity = connectivity)
                                }
                        )
                    } else {
                        logger.i { "ContactUser has logged out, stopping Service" }
                        appContext.stopService(Intent(appContext, BackgroundService::class.java))
                        Observable.empty<State>()
                    }
                }
                .logErrorAndForget()
                .subscribe(this::onStateChanged)


        appContext.receiveBroadcasts(Intent.ACTION_SCREEN_ON)
                .subscribe {
                    val roomState = signalService.currentWalkieRoomState.value
                    if (roomState.currentRoomId != null && roomState.status != RoomStatus.IDLE) {
                        logger.i { "Screen turning on and current in room. Open room activity." }
                        appContext.startActivity(Intent(appContext, RoomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
    }

    private fun onStateChanged(state: State) {
        logger.d { "State changed to $state" }

        val user = appComponent.signalBroker.currentUser.value.orNull()

        if (user == null) {
            logger.w { "ContactUser session has ended. Stopping service" }
            return
        }

        val builder = NotificationCompat.Builder(appContext)
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setContentTitle(R.string.app_name.toFormattedString(appContext))

        val icon: Int
        val text : String
        val intent : Intent

        val connectionState = appComponent.signalBroker.connectionState.value

        when {
            state.connectivity.not() || connectionState == SignalApi.ConnectionState.DISCONNECTED -> {
                text = appContext.getString(R.string.notification_user_offline, user.name)
                intent = Intent(appContext, HomeActivity::class.java)
                icon = R.drawable.ic_notification_offline
            }

            connectionState == SignalApi.ConnectionState.RECONNECTING -> {
                text = appContext.getString(R.string.notification_user_logging_in, user.name)
                intent = Intent(appContext, HomeActivity::class.java)
                icon = R.drawable.ic_notification_offline
            }

            state.videoRoomName != null -> {
                text = appContext.getString(R.string.video_chatting, state.videoRoomName)
                intent = Intent(appContext, CallActivity::class.java)
                icon = R.drawable.ic_videocam_white_24dp
            }

            state.walkieRoomName != null -> {
                text = appContext.getString(R.string.is_in_walkie_talkie, state.walkieRoomName)
                intent = Intent(appContext, RoomActivity::class.java)
                icon = R.drawable.ic_notification_joined_room
            }

            else -> {
                text = appContext.getString(R.string.notification_user_online, user.name)
                intent = Intent(appContext, HomeActivity::class.java)
                icon = R.drawable.ic_notification_logged_on
            }
        }

        builder.setSmallIcon(icon)
        builder.setContentText(text)
        builder.setContentIntent(PendingIntent.getActivity(appContext, 1, intent, 0))

        appContext.startService(Intent(appContext, BackgroundService::class.java).apply {
            action = BackgroundService.Companion.ACTION_UPDATE_NOTIFICATION
            putExtra(BackgroundService.Companion.EXTRA_NOTIFICATION, builder.build())
        })
    }

}