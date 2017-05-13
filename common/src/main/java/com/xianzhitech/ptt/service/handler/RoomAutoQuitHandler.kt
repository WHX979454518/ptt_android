package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.ActivityProvider
import com.xianzhitech.ptt.ui.base.BaseActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("RoomAutoQuitHandler")

/**
 * 处理自动退出房间的逻辑
 */
class RoomAutoQuitHandler(private val preference: Preference,
                          private val activityProvider: ActivityProvider,
                          private val signalBroker: SignalBroker) {


    init {
        signalBroker.currentWalkieRoomState
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

        signalBroker.currentWalkieRoomState
                .map(RoomState::status)
                .distinctUntilChanged()
                .switchMap {
                    if (it.inRoom && it != RoomStatus.ACTIVE) {
                        Observable.timer(Constants.ROOM_IDLE_TIME_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .subscribe {
                    if (signalBroker.peekWalkieRoomId() != null) {
                        logger.d { "Auto quit room because room is put to idle" }
                        signalBroker.quitWalkieRoom()
                        (activityProvider.currentStartedActivity as? BaseActivity)?.onCloseWalkieRoom()
                    }
                }

    }

    private fun onRoomStateChanged(lastRoomState: RoomState, currRoomState: RoomState) {
        logger.d { "Online member changed from ${lastRoomState.onlineMemberIds} to ${currRoomState.onlineMemberIds}" }

        if (lastRoomState.onlineMemberIds.size > 1 &&
                currRoomState.status.inRoom &&
                currRoomState.onlineMemberIds.size <= 1 &&
                preference.autoExit) {

            signalBroker.quitWalkieRoom()
            (activityProvider.currentStartedActivity as? BaseActivity)?.onCloseWalkieRoom()
        }
    }
}