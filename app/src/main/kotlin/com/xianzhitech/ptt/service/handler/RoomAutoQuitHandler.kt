package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * 处理自动退出房间的逻辑
 */
class RoomAutoQuitHandler(private val preference: Preference,
                          private val activityProvider: ActivityProvider,
                          private val roomRepository: RoomRepository,
                          private val signalServiceHandler: SignalServiceHandler) {

    private var lastRoomState : RoomState? = null

    init {
        signalServiceHandler.roomState
                .distinctUntilChanged { it.onlineMemberIds }
                .subscribe { onRoomStateChanged(it) }

        signalServiceHandler.roomState.map { it.currentRoomId }
                .distinctUntilChanged()
                .switchMap { roomRepository.getRoom(it).observe() }
                .map { it?.lastActiveTime }
                .switchMap {
                    if (it != null) {
                        Observable.timer(Constants.ROOM_IDLE_TIME_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .subscribeSimple {
                    if (signalServiceHandler.currentRoomId != null) {
                        logd("Auto quit room because room is put to idle")
                        signalServiceHandler.quitRoom()
                        (activityProvider.currentStartedActivity as? RoomActivity)?.finish()
                    }
                }

    }

    private fun onRoomStateChanged(roomState: RoomState) {
        if (lastRoomState?.let { it.currentRoomId == roomState.currentRoomId && it.onlineMemberIds.size > 1 } ?: false &&
                roomState.status.inRoom &&
                roomState.onlineMemberIds.size <= 1 &&
                preference.autoExit) {

            signalServiceHandler.quitRoom()
            (activityProvider.currentStartedActivity as? RoomActivity)?.finish()
        }

        lastRoomState = roomState
    }
}