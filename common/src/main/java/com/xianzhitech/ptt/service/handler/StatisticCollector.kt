package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.service.RoomState
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

/**
 * 统计房间相关的数据
 */
class StatisticCollector(signalService: SignalBroker) {
    var lastSpeakerId: String? = null
        private set

    private var lastSpeakerBeginTime: Date? = null
    private var lastSpeakerEndTime: Date? = null

    val lastSpeakerDuration: Long
        get() {
            return lastSpeakerBeginTime?.let {
                (lastSpeakerEndTime?.time ?: System.currentTimeMillis()) - it.time
            } ?: 0
        }

    init {
        signalService.currentWalkieRoomState
                .distinctUntilChanged(RoomState::speakerId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val currentSpeakerId = it.speakerId

                    if (lastSpeakerId != currentSpeakerId) {
                        if (lastSpeakerId == null) {
                            lastSpeakerBeginTime = Date()
                            lastSpeakerEndTime = null
                        } else if (currentSpeakerId == null) {
                            lastSpeakerEndTime = Date()
                        }
                    }

                    lastSpeakerId = currentSpeakerId
                }
    }
}