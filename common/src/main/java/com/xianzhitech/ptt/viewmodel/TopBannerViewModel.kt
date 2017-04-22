package com.xianzhitech.ptt.viewmodel

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.common.base.Optional
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.getConnectivityObservable
import com.xianzhitech.ptt.ext.i
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function5


class TopBannerViewModel(appComponent: AppComponent,
                         private val appContext: Context,
                         private val navigator : Navigator) : LifecycleViewModel() {
    private val signalBroker = appComponent.signalBroker
    private val storage = appComponent.storage

    val visible = ObservableBoolean()
    val text = ObservableField<String>()

    override fun onStart() {
        super.onStart()


        val notification: Observable<Triple<Optional<Pair<Room, String>>, Optional<Pair<Room, String>>, Boolean>> = Observable.combineLatest(
                signalBroker.connectionState,
                signalBroker.currentUser,
                signalBroker.currentVideoRoomId.switchMap { it.orNull()?.let { storage.getRoomWithName(it) } ?: Observable.just(Optional.absent())  } ,
                signalBroker.currentWalkieRoomId.switchMap { it.orNull()?.let { storage.getRoomWithName(it) } ?: Observable.just(Optional.absent())  },
                appContext.getConnectivityObservable(),
                Function5 { _, _, video, wt, connected -> Triple(video, wt, connected) }
        )

        notification
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (video, wt, connected) -> onStateChanged(video.orNull(), wt.orNull(), connected) }
                .bindToLifecycle()
    }

    private fun onStateChanged(videoRoom: Pair<Room, String>?,
                               walkieTalkieRoom: Pair<Room, String>?,
                               connected : Boolean) {
        logger.i { "State changed: videoRoom = $videoRoom, wk = $walkieTalkieRoom, connected = $connected" }

        val connectionState = signalBroker.connectionState.value

        when {
            connected.not() -> {
                visible.set(true)
                text.set(BaseApp.instance.getString(R.string.error_unable_to_connect))
            }

            connectionState == SignalApi.ConnectionState.CONNECTING ||
                    connectionState == SignalApi.ConnectionState.RECONNECTING -> {
                visible.set(true)
                text.set(BaseApp.instance.getString(R.string.connecting_to_server))
            }

            videoRoom != null -> {
                visible.set(true)
                text.set(BaseApp.instance.getString(R.string.video_chatting, videoRoom.second))
            }

            walkieTalkieRoom != null -> {
                visible.set(true)
                text.set(BaseApp.instance.getString(R.string.is_in_walkie_talkie, walkieTalkieRoom.second))
            }

            else -> {
                visible.set(false)
                text.set(null)
            }
        }
    }

    fun onClick() {
        if (signalBroker.currentWalkieRoomState.value.currentRoomId != null) {
            navigator.navigateToWalkieTalkiePage()
        }
        else if (signalBroker.currentVideoRoomId.value.isPresent) {
            navigator.navigateToVideoChatPage()
        }
    }

    interface Navigator {
        fun navigateToWalkieTalkiePage()
        fun navigateToVideoChatPage()
    }
}