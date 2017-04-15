package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.common.base.Optional
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.data.Room
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function4


class TopBannerViewModel(appComponent: AppComponent,
                         private val navigator : Navigator) : LifecycleViewModel() {
    private val signalBroker = appComponent.signalBroker
    private val storage = appComponent.storage

    val visible = ObservableBoolean()
    val text = ObservableField<String>()

    override fun onStart() {
        super.onStart()

        val notification: Observable<Pair<Optional<Pair<Room, String>>, Optional<Pair<Room, String>>>> = Observable.combineLatest(
                signalBroker.connectionState,
                signalBroker.currentUser,
                signalBroker.currentVideoRoom.switchMap(storage::getRoomWithName),
                signalBroker.currentWalkieTalkieRoom.switchMap(storage::getRoomWithName),
                Function4 { _, _, video, wt -> video to wt }
        )

        notification
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (video, wt) -> onStateChanged(video.orNull(), wt.orNull()) }
                .bindToLifecycle()
    }

    private fun onStateChanged(videoRoom: Pair<Room, String>?,
                               walkieTalkieRoom: Pair<Room, String>?) {
        val connectionState = signalBroker.connectionState.value

        when {
            connectionState == SignalApi.ConnectionState.CONNECTING ||
                    connectionState == SignalApi.ConnectionState.RECONNECTING -> {
                visible.set(true)
                text.set(App.instance.getString(R.string.connecting_to_server))
            }

            videoRoom != null -> {
                visible.set(true)
                text.set(App.instance.getString(R.string.video_chatting, videoRoom.second))
            }

            walkieTalkieRoom != null -> {
                visible.set(true)
                text.set(App.instance.getString(R.string.is_in_walkie_talkie, walkieTalkieRoom.second))
            }

            else -> {
                visible.set(false)
                text.set(null)
            }
        }
    }

    fun onClick() {
        if (signalBroker.currentWalkieTalkieRoom.value.isPresent) {
            navigator.navigateToWalkieTalkiePage()
        }
        else if (signalBroker.currentVideoRoom.value.isPresent) {
            navigator.navigateToVideoChatPage()
        }
    }

    interface Navigator {
        fun navigateToWalkieTalkiePage()
        fun navigateToVideoChatPage()
    }
}