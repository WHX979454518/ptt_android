package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.data.Room


data class VoiceServerConfig(
        @param:JsonProperty("host")
        val host: String,

        @param:JsonProperty("port")
        val port: Int,

        @param:JsonProperty("protocol")
        val protocol: String,

        @param:JsonProperty("tcpPort")
        val tcpPort: Int
)

data class JoinWalkieRoomResponse(
        @param:JsonProperty("room")
        val room : Room,

        @param:JsonProperty("initiatorUserId")
        val initiatorUserId: String,

        @param:JsonProperty("onlineMemberIds")
        val onlineMemberIds: Set<String>,

        @param:JsonProperty("speakerId")
        val speakerId : String?,

        @param:JsonProperty("speakerPriority")
        val speakerPriority : Int?,

        @param:JsonProperty("voiceServer")
        val voiceServerConfig: VoiceServerConfig
)