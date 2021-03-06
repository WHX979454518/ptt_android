package com.xianzhitech.ptt.data

import android.os.Parcelable
import com.baidu.mapapi.utils.CoordinateConverter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.xianzhitech.ptt.api.event.Event
import com.xianzhitech.ptt.util.MessageBodyConverter
import com.xianzhitech.ptt.util.SetConverter
import io.requery.Column
import io.requery.Convert
import io.requery.Entity
import io.requery.ForeignKey
import io.requery.Generated
import io.requery.Index
import io.requery.Key
import io.requery.Persistable
import io.requery.ReadOnly
import io.requery.ReferentialAction
import io.requery.Table
import java.io.Serializable
import java.util.*

interface NamedModel {
    val id: String
    val name: String
}

interface User : NamedModel {
    override val id: String
    override val name: String

    val avatar: String?
    val priority: Int

    val phoneNumber: String?
}


@Entity
@Table(name = "users")
@JsonDeserialize(`as` = ContactUserEntity::class)
interface ContactUser : User, Persistable, NamedModel, Parcelable, Serializable {
    @get:JsonProperty("idNumber")
    @get:Key
    override val id: String

    @get:JsonProperty("name")
    override val name: String

    @get:JsonProperty("avatar")
    override val avatar: String?

    @get:JsonProperty("priority")
    override val priority: Int

    @get:JsonProperty("father")
    val parentObjectId: String?

    @get:JsonProperty("phoneNumber")
    override val phoneNumber: String?
}

@Entity
@Table(name = "groups")
@JsonDeserialize(`as` = ContactGroupEntity::class)
interface ContactGroup : Persistable, NamedModel, Parcelable, Serializable {
    @get:Key
    @get:JsonProperty("idNumber")
    override val id: String

    @get:JsonProperty("name")
    override val name: String

    @get:JsonProperty("avatar")
    val avatar: String?

    @get:JsonProperty("members")
    @get:Convert(SetConverter::class)
    val memberIds: Set<String>
}

data class ContactEnterprise(val departments: Collection<ContactDepartment>,
                             val directUsers: Collection<ContactUser>,
                             val name: String)

data class ContactDepartment @JvmOverloads constructor(
        @get:JsonProperty("_id") val id: String = "",
        @get:JsonProperty("name") val name: String = "",
        @get:JsonProperty("father") val parentObjectId: String? = null,
        @JsonIgnore val children: MutableCollection<ContactDepartment> = linkedSetOf(),
        @JsonIgnore val members: MutableCollection<ContactUser> = linkedSetOf())

@Entity
@Table(name = "rooms")
@JsonDeserialize(`as` = RoomEntity::class)
interface Room : Persistable, Parcelable, Event, Serializable {
    @get:JsonProperty("idNumber")
    @get:Key
    val id: String

    @get:JsonProperty("associatedGroupIds")
    @get:Convert(SetConverter::class)
    val groupIds: Set<String>

    @get:JsonProperty("extraMemberIds")
    @get:Convert(SetConverter::class)
    val extraMemberIds: Set<String>

    @get:JsonProperty("name")
    val name: String?

    @get:JsonProperty("ownerId")
    val ownerId: String
}

@Entity
@Table(name = "room_info")
interface RoomInfo : Persistable, Parcelable, Serializable {
    @get:ForeignKey(references = Room::class, delete = ReferentialAction.CASCADE)
    val roomId: String

    @get:ForeignKey(references = Message::class, referencedColumn = "remoteId", delete = ReferentialAction.CASCADE)
    val latestReadMessageRemoteId: String?

    val lastWalkieActiveTime: Date?
}


@Entity
@Table(name = "messages")
@JsonDeserialize(`as` = MessageEntity::class)
interface Message : Persistable, Serializable, Event {
    @get:JsonIgnore
    @get:Key
    @get:Generated
    val id: Long?

    @get:JsonProperty("localId")
    @get:Column(definition = "UNIQUE ON CONFLICT REPLACE")
    val localId: String?

    @get:JsonProperty("_id")
    @get:Column(definition = "UNIQUE ON CONFLICT REPLACE")
    val remoteId: String?

    @get:Index
    @get:JsonProperty("sendTime")
    val sendTime: Date

    @get:JsonProperty("type", defaultValue = "unknown")
    @get:MessageType.Type
    val type: String

    @get:JsonProperty("body")
    @get:JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @get:Convert(MessageBodyConverter::class)
    val body: MessageBody?

    @get:JsonProperty("senderId")
    @get:Index
    val senderId: String

    @get:JsonProperty("roomId")
    @get:Column(nullable = false)
    @get:ForeignKey(references = Room::class, delete = ReferentialAction.CASCADE)
    val roomId: String

    @get:JsonIgnore
    @get:Index
    @get:ReadOnly
    @get:Column(value = "0")
    val hasRead: Boolean

    @get:JsonIgnore
    val error: Boolean
}


val Room.isSingle: Boolean
    get() = groupIds.isEmpty() && extraMemberIds.size < 3


data class LatLng(@get:JsonProperty("lat") val lat: Double = Double.MIN_VALUE,
                  @get:JsonProperty("lng") val lng: Double = Double.MIN_VALUE) : Serializable {

    fun convertToBaidu() : com.baidu.mapapi.model.LatLng {
        return CoordinateConverter()
                .from(CoordinateConverter.CoordType.GPS)
                .coord(com.baidu.mapapi.model.LatLng(lat, lng))
                .convert()
    }

    companion object {
        @JvmStatic val EMPTY = LatLng()
    }
}

data class Location @JvmOverloads constructor(@get:JsonUnwrapped val latLng: LatLng = LatLng.EMPTY,
                                              @get:JsonProperty("radius") val radius: Int? = null,
                                              @get:JsonProperty("alt") val altitude: Int? = null,
                                              @get:JsonProperty("speed") val speed: Int? = null,
                                              @get:JsonProperty("repTime") val time: Long? = null,
                                              @get:JsonProperty("direction") val direction: Float? = null) : Serializable {


    companion object {
        @JvmStatic val EMPTY = Location()

        fun from(location: android.location.Location): Location {
            return Location(
                    latLng = LatLng(lat = location.latitude, lng = location.longitude),
                    radius = location.accuracy.toInt(),
                    altitude = location.altitude.toInt(),
                    speed = location.speed.toInt(),
                    time = location.time,
                    direction = location.bearing
            )
        }
    }
}