package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*


enum class MessageType(val bodyClass : Class<*>? = null) {
    @JsonProperty("text")
    TEXT(TextMessage::class.java),

    @JsonProperty("image")
    IMAGE(MediaMessage::class.java),

    @JsonProperty("video")
    VIDEO(MediaMessage::class.java),

    @JsonProperty("location")
    LOCATION(LocationMessage::class.java),

    @JsonProperty("notify_create_room")
    NOTIFY_CREATE_ROOM,

    ;

    companion object {
        val MEANINGFUL : Set<MessageType> = EnumSet.of(TEXT, IMAGE, VIDEO, LOCATION)
    }
}

data class TextMessage(@param:JsonProperty("text") val text : String?)
data class MediaMessage(@param:JsonProperty("url") val url : String,
                        @param:JsonProperty("desc") val desc: String?)

data class LocationMessage(@param:JsonProperty("lat") val lat : Double,
                           @param:JsonProperty("lng") val lng : Double,
                           @param:JsonProperty("address") val address : String?)

fun <T> Message.convertBody(objectMapper: ObjectMapper) : T? {
    if (body == null || type == null || type!!.bodyClass == null) {
        return null
    }

    @Suppress("UNCHECKED_CAST")
    return objectMapper.convertValue(body, type!!.bodyClass) as? T
}
