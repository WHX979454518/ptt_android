package com.xianzhitech.ptt.maintain.service.dto

interface RoomSpeakerUpdate {
    val roomId: String
    val speakerId: String?
    val speakerPriority : Int?
}