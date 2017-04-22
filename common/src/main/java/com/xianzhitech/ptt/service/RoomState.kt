package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.api.dto.VoiceServerConfig
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.Permission

enum class RoomStatus(val inRoom: Boolean) {
    IDLE(false),
    JOINING(true),
    JOINED(true),
    REQUESTING_MIC(true),
    ACTIVE(true),
}

data class RoomState(val status: RoomStatus,
                     val currentRoomId: String?,
                     val currentRoomInitiatorUserId : String?,
                     val speakerId: String?,
                     val speakerPriority : Int?,
                     val onlineMemberIds: Set<String>,
                     val voiceServer: VoiceServerConfig?) {
    companion object {
        @JvmStatic val EMPTY = RoomState(RoomStatus.IDLE, null, null, null, null, emptySet(), null)
    }

    init {
        if ((status.inRoom && currentRoomId == null) ||
                (status == RoomStatus.ACTIVE && speakerId == null) ||
                (currentRoomId == null && speakerId != null)) {
            throw IllegalStateException("RoomState is not valid")
        }
    }

    fun canRequestMic(user : CurrentUser?) : Boolean {
        if (user == null || currentRoomId == null || speakerId == user.id || user.hasPermission(Permission.SPEAK).not()) {
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