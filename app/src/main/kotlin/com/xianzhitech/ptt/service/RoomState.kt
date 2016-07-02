package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User

data class RoomState(val status: RoomStatus,
                     val currentRoomId: String?,
                     val currentRoomInitiatorUserId : String?,
                     val speakerId: String?,
                     val speakerPriority : Int?,
                     val onlineMemberIds: Set<String>,
                     val voiceServer: Map<String, Any?>) {
    companion object {
        @JvmStatic val EMPTY = RoomState(RoomStatus.IDLE, null, null, null, null, emptySet(), emptyMap())
    }

    init {
        if ((status.inRoom && currentRoomId == null) ||
//                (currentRoomId != null && currentRoomInitiatorUserId == null) ||
                (status == RoomStatus.ACTIVE && speakerId == null) ||
//                (currentRoomId == null && currentRoomInitiatorUserId != null) ||
                (currentRoomId == null && speakerId != null)) {
            throw IllegalStateException("RoomState is not valid")
        }
    }

    fun canRequestMic(user : User?) : Boolean {
        if (user == null || currentRoomId == null || speakerId == user.id) {
            return false
        }

        if (status != RoomStatus.ACTIVE && status != RoomStatus.JOINED) {
            return false
        }

        if (speakerId == null || user.priority == 0) {
            return true
        }

        if (speakerPriority == null) {
            return false
        }

        return user.priority > speakerPriority
    }
}