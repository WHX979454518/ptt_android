package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonProperty


data class WalkieRoomSpeakerUpdateEvent(
        @param:JsonProperty("roomId")
        val roomId : String,

        @param:JsonProperty("speakerId")
        val speakerId : String?,

        @param:JsonProperty("speakerPriority")
        val speakerPriority : Int?
) : Event