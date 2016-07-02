package com.xianzhitech.ptt.service.dto

import com.xianzhitech.ptt.model.Room

interface JoinRoomResult {
    val room: Room
    val initiatorUserId : String
    val onlineMemberIds: Collection<String>
    val speakerId: String?
    val speakerPriority: Int?
    val voiceServerConfiguration: Map<String, Any>
}