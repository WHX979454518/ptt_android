package com.xianzhitech.ptt.service

data class RoomState(val status : RoomStatus = RoomStatus.IDLE,
                     val currentRoomID: String? = null,
                     val currentRoomActiveSpeakerID: String? = null,
                     val currentRoomOnlineMemberIDs: Set<String> = emptySet()) {
}