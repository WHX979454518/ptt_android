package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.data.Room
import java.util.*


data class WalkieRoomInvitationEvent(
        @param:JsonProperty("room")
        val room: Room,

        @param:JsonProperty("inviterId")
        val inviterId: String,

        @param:JsonProperty("inviterPriority")
        val inviterPriority: Int,

        @param:JsonProperty("force")
        val force: Boolean
) : Event {

    @get:JsonIgnore
    val inviteTime = Date()
}