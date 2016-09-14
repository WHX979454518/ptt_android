package com.xianzhitech.ptt.maintain.service.handler

import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.maintain.service.RoomState
import com.xianzhitech.ptt.maintain.service.RoomStatus
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("RoomAutoQuitHandler")

/**
 * 处理自动退出房间的逻辑
 */
class RoomAutoQuitHandler(private val preference: Preference,
                          private val activityProvider: ActivityProvider,
                          private val signalServiceHandler: SignalServiceHandler) {


    init {
        signalServiceHandler.roomState
                .doOnNext { logger.d { "Room state changed to $it" } }
                .scan(ArrayList<RoomState>(2), { result, newState ->
                    if (newState.status.inRoom.not()) {
                        result.clear()
                    }
                    else if (result.isEmpty() || result.last().onlineMemberIds != newState.onlineMemberIds) {
                        if (result.size <= 1) {
                            result.add(newState)
                        } else {
                            result[0] = result[1]
                            result[1] = newState
                        }
                    }
                    result
                 })
                .switchMap { states ->
                    if (states.size == 2 && states[1].onlineMemberIds.size <= 1) {
                        // 如果是只剩下一个成员了, 先等一等看还有没有人进来
                        Observable.timer(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).map { states }
                    } else if (states.size == 2) {
                        Observable.just(states)
                    } else {
                        Observable.never()
                    }
                }
                .subscribe { onRoomStateChanged(it[0], it[1]) }

        signalServiceHandler.roomStatus
                .switchMap {
                    if (it.inRoom && it != RoomStatus.ACTIVE) {
                        Observable.timer(Constants.ROOM_IDLE_TIME_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .subscribeSimple {
                    if (signalServiceHandler.peekCurrentRoomId() != null) {
                        logger.d { "Auto quit room because room is put to idle" }
                        signalServiceHandler.quitRoom()
                        (activityProvider.currentStartedActivity as? RoomActivity)?.finish()
                    }
                }

    }

    private fun onRoomStateChanged(lastRoomState: RoomState, currRoomState: RoomState) {
        logger.d { "Online member changed from ${lastRoomState.onlineMemberIds} to ${currRoomState.onlineMemberIds}" }

        if (lastRoomState.onlineMemberIds.size > 1 &&
                currRoomState.status.inRoom &&
                currRoomState.onlineMemberIds.size <= 1 &&
                preference.autoExit) {

            signalServiceHandler.quitRoom()
            (activityProvider.currentStartedActivity as? RoomActivity)?.finish()
        }
    }
}