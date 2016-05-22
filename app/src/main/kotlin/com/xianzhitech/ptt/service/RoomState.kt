package com.xianzhitech.ptt.service

data class RoomState(val status : RoomStatus,
                     val currentRoomId: String?,
                     val speakerId: String?,
                     val onlineMemberIds: Set<String>,
                     val voiceServer : Map<String, Any?>) {
    companion object {
        @JvmStatic val EMPTY = RoomState(RoomStatus.IDLE, null, null, emptySet(), emptyMap())
    }
}