package com.xianzhitech.ptt.service

enum class RoomStatus(val inRoom: Boolean) {
    IDLE(false),
    JOINING(true),
    JOINED(true),
    REQUESTING_MIC(true),
    ACTIVE(true),
    OFFLINE(false),
}