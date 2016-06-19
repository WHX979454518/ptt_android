package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User

data class RoomState(val status: RoomStatus,
                     val currentRoomId: String?,
                     val speakerId: String?,
                     val speakerPriority : Int?,
                     val onlineMemberIds: Set<String>,
                     val voiceServer: Map<String, Any?>) {
    companion object {
        @JvmStatic val EMPTY = RoomState(RoomStatus.IDLE, null, null, null, emptySet(), emptyMap())
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