package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper


enum class MessageType(val bodyClass : Class<*>? = null) {
    @JsonProperty("text")
    TEXT(TextMessage::class.java),

    @JsonProperty("notify_create_room")
    NOTIFY_CREATE_ROOM
}

data class TextMessage(@param:JsonProperty("text") val text : String?)

fun <T> Message.convertBody(objectMapper: ObjectMapper) : T? {
    if (body == null || type == null || type!!.bodyClass == null) {
        return null
    }

    @Suppress("UNCHECKED_CAST")
    return objectMapper.convertValue(body, type!!.bodyClass) as? T
}
