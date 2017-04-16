package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonProperty


data class WalkieRoomActiveInfoUpdateEvent(
        @param:JsonProperty("roomId")
        val roomId : String,

        @param:JsonProperty("speakerId")
        val speakerId : String?,

        @param:JsonProperty("speakerPriority")
        val speakerPriority : Int?,

        @param:JsonProperty("onlineMemberIds")
        val onlineMemberIds : Set<String>
) : Event