package com.xianzhitech.ptt.data

import android.content.Context
import android.support.annotation.StringDef
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.xianzhitech.ptt.R
import java.io.Serializable


object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val LOCATION = "location"
    const val NOTIFY_JOIN_ROOM = "join_room"
    const val NOTIFY_QUIT_ROOM = "quit_room"
    const val NOTIFY_START_VIDEO_CHAT = "start_video_chat"
    const val NOTIFY_END_VIDEO_CHAT = "end_video_chat"
    const val NOTIFY_GRAB_MIC = "grab_mic"
    const val NOTIFY_RELEASE_MIC = "release_mic"
    const val NOTIFY_ADDED_ROOM_MEMBERS = "add_room_members"

    val MEANINGFUL: Set<String> = setOf(TEXT, IMAGE, VIDEO, LOCATION)

    @StringDef(
            MessageType.IMAGE,
            MessageType.TEXT,
            MessageType.LOCATION,
            MessageType.NOTIFY_GRAB_MIC,
            MessageType.NOTIFY_RELEASE_MIC,
            MessageType.NOTIFY_JOIN_ROOM,
            MessageType.NOTIFY_QUIT_ROOM,
            MessageType.NOTIFY_START_VIDEO_CHAT,
            MessageType.NOTIFY_END_VIDEO_CHAT
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

}

@JsonSubTypes(
        JsonSubTypes.Type(name = "text", value = TextMessageBody::class),
        JsonSubTypes.Type(name = "image", value = ImageMessageBody::class),
        JsonSubTypes.Type(name = "video", value = VideoMessageBody::class),
        JsonSubTypes.Type(name = "location", value = LocationMessageBody::class)
)
interface MessageBody : Serializable {
    fun toDisplayText(context: Context): CharSequence
}

data class TextMessageBody @JvmOverloads constructor(
        @get:JsonProperty("text") val text: String = "") : MessageBody {
    override fun toDisplayText(context: Context) = text
}

interface MediaMessageBody : MessageBody {
    val url: String
    val thumbnail: String?
    val desc: String?
}

data class ImageMessageBody @JvmOverloads constructor(
        @get:JsonProperty("url") override val url: String = "",
        @get:JsonProperty("thumbnail") override val thumbnail: String? = null,
        @get:JsonProperty("desc") override val desc: String? = null) : MediaMessageBody {
    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.image_body)
        } else {
            desc!!
        }
    }
}

data class VideoMessageBody @JvmOverloads constructor(
        @get:JsonProperty("url") override val url: String = "",
        @get:JsonProperty("thumbnail") override val thumbnail: String? = null,
        @get:JsonProperty("desc") override val desc: String? = null) : MediaMessageBody {
    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.video_body)
        } else {
            desc!!
        }
    }
}

data class LocationMessageBody @JvmOverloads constructor(
        @get:JsonProperty("loc") val loc: Location = Location.EMPTY,
        @get:JsonProperty("desc") val desc: String? = null) : MessageBody {

    val isEmpty: Boolean
        get() = loc == Location.EMPTY

    override fun toDisplayText(context: Context): CharSequence {
        return if (desc.isNullOrBlank()) {
            context.getString(R.string.location_body)
        } else {
            desc!!
        }
    }
}

data class AddRoomMembersMessageBody @JvmOverloads constructor(
        @get:JsonProperty("memberIds") val memberIds: List<String> = emptyList()) : MessageBody {
    override fun toDisplayText(context: Context): CharSequence {
        return ""
    }
}

fun Message.copy(copier: MessageEntity.(Message) -> Unit = {}): Message {
    val src = this
    return MessageEntity().apply {
        setLocalId(src.localId)
        setRemoteId(src.remoteId)
        setBody(src.body)
        setError(src.error)
        setSendTime(src.sendTime)
        setSenderId(src.senderId)
        setType(src.type)
        setRoomId(src.roomId)
        this.copier(src)
    }
}