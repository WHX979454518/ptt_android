package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty


enum class MessageType(val bodyClass : Class<*>? = null) {
    @JsonProperty("text")
    TEXT(TextMessage::class.java),

    @JsonProperty("notify_create_room")
    NOTIFY_CREATE_ROOM
}

data class TextMessage(@param:JsonProperty("text") val text : String)
