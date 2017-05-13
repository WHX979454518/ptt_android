package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.widget.Toast
import com.google.common.base.Optional
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.NoSuchRoomException
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import java.util.concurrent.TimeUnit


class WalkieRoomViewModel(private val appComponent: AppComponent,
                          private var requestJoinRoomId: String?,
                          private var requestJoinRoomFromInvitation: Boolean,
                          private var pendingInvitations : List<WalkieRoomInvitationEvent>,
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
    val hasCurrentSpeaker = ObservableBoolean()
    val displaySpeakerView = ObservableBoolean()
    val onlineMembers = ObservableArrayList<User>()
    val isSpeakerMe = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        if (requestJoinRoomId == null && signalBroker.peekWalkieRoomId() == null) {
            logger.i { "Room no longer" }
            navigator.navigateToRoomNoLongerExistsPage()
            return
        }

        if (requestJoinRoomId != null && requestJoinRoomId != signalBroker.peekWalkieRoomId()) {
            joinRoom(requestJoinRoomId!!, requestJoinRoomFromInvitation)
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

        currentRoom
                .switchMap {
                    if (it.isPresent) {
                        Observable.just(it)
                    } else {
                        Observable.just(it).delay(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it.isAbsent) {
                        navigator.navigateToRoomNoLongerExistsPage()
                    }
                }
                .bindToLifecycle()

        signalBroker.currentWalkieRoomState
                .map(RoomState::onlineMemberIds)
                .distinctUntilChanged()
                .switchMap(storage::getUsers)
                .logErrorAndForget()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onlineMembers::replaceAll)
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
                                .map { DateUtils.formatElapsedTime((SystemClock.elapsedRealtime() - state.speakerStartTime) / 1000L) }
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
                .share()

        currentSpeaker
                .logErrorAndForget()
                .subscribe {
                    if (it.isPresent) {
                        this.currentSpeaker.set(it.get())
                    }
                    this.isSpeakerMe.set(it.orNull()?.id == appComponent.signalBroker.peekUserId())
                    this.hasCurrentSpeaker.set(it.isPresent)
                }
                .bindToLifecycle()

        currentSpeaker
                .map { it.isPresent }
                .distinctUntilChanged()
                .switchMap { hasSpeaker ->
                    if (hasSpeaker) {
                        Observable.just(hasSpeaker)
                    } else {
                        Observable.just(hasSpeaker).delay(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    }
                }
                .logErrorAndForget()
                .subscribe(displaySpeakerView::set)
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

    fun joinRoom(roomId: String, fromInvitation: Boolean) {
        signalBroker.joinWalkieRoom(roomId, fromInvitation)
                .doOnLoading(isLoading::set)
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget(navigator::navigateToErrorPage)
                .subscribe()
                .bindToLifecycle()
    }

    fun onClickSpeakerView() {
        currentSpeaker.get()?.id?.let(navigator::navigateToUserDetailsPage)
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

    fun onClickLeaveRoom() {
        appComponent.signalBroker.quitWalkieRoom()
        navigator.closeRoomPage()
    }

    fun onClickViewMember() {
        appComponent.signalBroker.peekWalkieRoomId()?.let(navigator::navigateToRoomMemberPage)
    }

    fun onClickNotification() {
        val result = appComponent.signalBroker.peekWalkieRoomId()?.let(appComponent.signalBroker::inviteRoomMembers) ?: Single.error(NoSuchRoomException(null))
        result.observeOn(AndroidSchedulers.mainThread())
                .subscribe { invitedCount, err ->
                    if (err != null) {
                        err.toast()
                        return@subscribe
                    }

                    if (invitedCount > 0) {
                        Toast.makeText(BaseApp.instance, BaseApp.instance.getString(R.string.member_invitation_sent, invitedCount), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(BaseApp.instance, R.string.member_invitation_sent_none, Toast.LENGTH_LONG).show()
                    }
                }
    }

    fun onClickTitle() {
        navigator.showOnlinePopupWindow()
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
        fun closeRoomPage()
        fun navigateToRoomMemberPage(roomId : String)
        fun displayNoPermissionError()
        fun showOnlinePopupWindow()
        fun navigateToUserDetailsPage(userId: String)
    }

    companion object {
        private const val STATE_REQUEST_JOIN_ROOM_ID = "request_join_room_id"
    }
}