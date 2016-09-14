package com.xianzhitech.ptt.maintain.service.handler

import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.repo.RoomRepository
import java.util.*

/**
 * 记录房间的对话
 */
class RoomStatusHandler(private val roomRepository: RoomRepository,
                        private val signalService: SignalServiceHandler) {
    init {
        signalService.roomState.distinctUntilChanged { it -> it.speakerId }
                .filter { it.speakerId != null && it.currentRoomId != null }
                .subscribeSimple {
                    roomRepository.updateLastRoomSpeaker(it.currentRoomId!!, Date(), it.speakerId!!).execAsync().subscribeSimple()
                }
    }
}