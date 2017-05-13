package com.xianzhitech.ptt.broker

import com.fasterxml.jackson.annotation.JsonProperty


enum class RoomMode {
    @JsonProperty("normal")
    NORMAL,

    @JsonProperty("emergency")
    EMERGENCY,

    @JsonProperty("broadcast")
    BROADCAST,

    @JsonProperty("system_call")
    SYSTEM_CALL,

    @JsonProperty("video_chat")
    VIDEO,

    @JsonProperty("audio_chat")
    AUDIO,
}