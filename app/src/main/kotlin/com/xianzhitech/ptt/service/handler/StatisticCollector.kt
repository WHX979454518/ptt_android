package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.SignalService
import java.util.*

/**
 * 统计房间相关的数据
 */
class StatisticCollector(private val signalService: SignalService) {
    var lastSpeakerId : String? = null
    private set

    private var lastSpeakerBeginTime : Date? = null
    private var lastSpeakerEndTime : Date? = null

    val lastSpeakerDuration: Long
        get() {
            return lastSpeakerBeginTime?.let {
                (lastSpeakerEndTime?.time ?: System.currentTimeMillis()) - it.time
            } ?: 0
        }

    init {
        signalService.roomState.distinctUntilChanged { it.speakerId }
            .observeOnMainThread()
            .subscribeSimple {
                val currentSpeakerId = it.speakerId

                if (lastSpeakerId != currentSpeakerId) {
                    if (lastSpeakerId == null) {
                        lastSpeakerBeginTime = Date()
                        lastSpeakerEndTime = null
                    }
                    else if (currentSpeakerId == null) {
                        lastSpeakerEndTime = Date()
                    }
                }

                lastSpeakerId = currentSpeakerId
            }
    }
}