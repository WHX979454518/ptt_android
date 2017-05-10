package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import com.google.common.base.Optional
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.combineLatest
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toOptional
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import java.util.concurrent.TimeUnit


class WalkieRoomViewModel(private val appComponent: AppComponent,
                          private var requestJoinRoomId: String?,
                          private var requestJoinRoomFromInvitation: Boolean,
                          private val navigator: Navigator) : LifecycleViewModel() {
    private val signalBroker = appComponent.signalBroker

    val isLoading = ObservableBoolean()
    val isRequestingMic = ObservableBoolean()
    val isOffline = ObservableBoolean()
    val title = ObservableField<String>()
    val onlineUserNumber = ObservableInt()
    val roomMemberNumber = ObservableInt()
    val currentSpeakingDuration = ObservableField<String>()
    val canGrabMic = ObservableBoolean()
    val currentSpeaker = ObservableField<User>()

    override fun onStart() {
        super.onStart()

        if (requestJoinRoomId == null && signalBroker.peekWalkieRoomId() == null) {
            logger.i { "Room no longer" }
            navigator.navigateToRoomNoLongerExistsPage()
            return
        }

        if (requestJoinRoomId != signalBroker.peekWalkieRoomId()) {
            signalBroker.joinWalkieRoom(requestJoinRoomId!!, requestJoinRoomFromInvitation)
                    .doOnLoading(isLoading::set)
                    .observeOn(AndroidSchedulers.mainThread())
                    .logErrorAndForget(navigator::navigateToErrorPage)
                    .subscribe()
                    .bindToLifecycle()

            requestJoinRoomId = null
        } else {
            isLoading.set(false)
        }

        val storage = appComponent.storage
        val currentRoom = signalBroker.currentWalkieRoomId.switchMap {
            if (it.isPresent) {
                storage.getRoom(it.get())
            } else {
                Observable.just(Optional.absent())
            }
        }.replay(1).refCount()

        currentRoom.switchMap { it.orNull()?.let(storage::getRoomName) ?: Observable.just("") }
                .logErrorAndForget()
                .subscribe(title::set)
                .bindToLifecycle()

        signalBroker.currentWalkieRoomState
                .subscribe { onlineUserNumber.set(if (it.status.inRoom) it.onlineMemberIds.size else 0) }
                .bindToLifecycle()

        currentRoom.switchMap { it.orNull()?.let(storage::getRoomMemberNumber) ?: Observable.just(0) }
                .logErrorAndForget()
                .subscribe(roomMemberNumber::set)
                .bindToLifecycle()

        signalBroker.currentWalkieRoomState
                .distinctUntilChanged(Function<RoomState, String?> { it.speakerId })
                .switchMap { state ->
                    if (state.speakerId != null && state.speakerStartTime > 0) {
                        Observable.interval(0, 1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                                .map { DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() - state.speakerStartTime) }
                    } else {
                        Observable.just("")
                    }
                }
                .logErrorAndForget()
                .subscribe(currentSpeakingDuration::set)

        val currentSpeaker = signalBroker.currentWalkieRoomState
                .map { it.speakerId.toOptional() }
                .distinctUntilChanged()
                .switchMap { it.orNull()?.let(storage::getUser) ?: Observable.just(Optional.absent()) }
                .replay(1)
                .refCount()

        currentSpeaker
                .logErrorAndForget()
                .subscribe { this.currentSpeaker.set(it.orNull()) }
                .bindToLifecycle()

        combineLatest(
                signalBroker.currentUser,
                signalBroker.currentWalkieRoomState,
                { currUser, state -> state.canRequestMic(currUser.orNull()) })
                .subscribe(canGrabMic::set)
                .bindToLifecycle()

        signalBroker.currentWalkieRoomState
                .map { it.status == RoomStatus.REQUESTING_MIC }
                .distinctUntilChanged()
                .subscribe(isRequestingMic::set)
                .bindToLifecycle()
    }

    fun onClickRequestMic() {
        if (!signalBroker.currentWalkieRoomState.value.canRequestMic(signalBroker.currentUser.value.orNull())) {
            navigator.displayNoPermissionError()
            return
        }

        appComponent.signalBroker
                .grabWalkieMic()
                .toCompletable()
                .logErrorAndForget()
                .subscribe()
    }

    override fun onSaveState(out: Bundle) {
        super.onSaveState(out)

        out.putString(STATE_REQUEST_JOIN_ROOM_ID, requestJoinRoomId)
    }

    override fun onRestoreState(state: Bundle) {
        super.onRestoreState(state)

        requestJoinRoomId = state.getString(STATE_REQUEST_JOIN_ROOM_ID)
    }

    interface Navigator {
        fun navigateToRoomNoLongerExistsPage()
        fun navigateToErrorPage(throwable: Throwable)
        fun displayNoPermissionError()
    }

    companion object {
        private const val STATE_REQUEST_JOIN_ROOM_ID = "request_join_room_id"
    }
}