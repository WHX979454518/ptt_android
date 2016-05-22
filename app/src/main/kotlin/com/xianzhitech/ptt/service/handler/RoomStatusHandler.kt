package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.SignalService
import java.util.*

/**
 * 记录房间的对话
 */
class RoomStatusHandler(private val roomRepository: RoomRepository,
                        private val signalService: SignalService) {
    init {
        signalService.roomState.distinctUntilChanged { it.speakerId }
                .filter { it.speakerId != null && it.currentRoomId != null }
                .subscribeSimple {
                    roomRepository.updateLastRoomSpeaker(it.currentRoomId!!, Date(), it.speakerId!!).execAsync().subscribeSimple()
                }
    }
}