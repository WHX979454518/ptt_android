package com.xianzhitech.ptt.data

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.xianzhitech.ptt.R
import java.io.Serializable
import java.util.*


enum class MessageType {
    @JsonProperty("text")
    TEXT,

    @JsonProperty("image")
    IMAGE,

    @JsonProperty("video")
    VIDEO,

    @JsonProperty("location")
    LOCATION,

    @JsonProperty("notify_create_room")
    NOTIFY_CREATE_ROOM,

    ;

    companion object {
        val MEANINGFUL: Set<MessageType> = EnumSet.of(TEXT, IMAGE, VIDEO, LOCATION)
    }
}

@JsonSubTypes(
        JsonSubTypes.Type(name = "text", value = TextMessageBody::class),
        JsonSubTypes.Type(name = "image", value = ImageMessageBody::class),
        JsonSubTypes.Type(name = "video", value = VideoMessageBody::class),
        JsonSubTypes.Type(name = "location", value = LocationMessageBody::class)
)
interface MessageBody : Serializable {
    fun toDisplayText(context: Context): CharSequence

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class TextMessageBody(@JsonProperty("text") val text: String) : MessageBody {
    override fun toDisplayText(context: Context) = text
}

data class ImageMessageBody(@JsonProperty("url") val url: String,
                            @JsonProperty("desc") val desc: String) : MessageBody {
    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.image_body)
        } else {
            desc
        }
    }
}

data class VideoMessageBody(@JsonProperty("url") val url: String,
                            @JsonProperty("desc") val desc: String) : MessageBody {
    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.image_body)
        } else {
            desc
        }
    }
}

data class LocationMessageBody(@JsonProperty("lat") val lat: Double,
                               @JsonProperty("lng") val lng: Double,
                               @JsonProperty("desc") val desc: String?) : MessageBody {

    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.location_body)
        } else {
            desc!!
        }
    }
}