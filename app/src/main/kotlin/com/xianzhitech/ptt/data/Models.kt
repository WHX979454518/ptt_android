package com.xianzhitech.ptt.data

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.xianzhitech.ptt.util.SetConverter
import io.requery.*
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
interface ContactUser : User, Persistable, NamedModel, Parcelable {
    @get:JsonProperty("idNumber")
    @get:Key
    override val id: String

    @get:JsonProperty("name")
    override val name: String

    @get:JsonProperty("avatar")
    override val avatar: String?

    @get:JsonProperty("priority")
    override val priority: Int

    @get:JsonProperty("phoneNumber")
    override val phoneNumber: String?
}

@Entity
@Table(name = "groups")
@JsonDeserialize(`as` = ContactGroupEntity::class)
interface ContactGroup : Persistable, NamedModel, Parcelable {
    @get:Key
    @get:JsonProperty("idNumber")
    override val id: String

    @get:JsonProperty("name")
    override val name: String

    @get:JsonProperty("avatar")
    val avatar : String?

    @get:JsonProperty("members")
    @get:Convert(SetConverter::class)
    val memberIds : Set<String>
}

@Entity
@Table(name = "rooms")
@JsonDeserialize(`as` = RoomEntity::class)
interface Room : Persistable, Parcelable {
    @get:JsonProperty("idNumber")
    @get:Key
    val id: String

    @get:JsonProperty("associatedGroupIds")
    @get:Convert(SetConverter::class)
    val groupIds : Set<String>

    @get:JsonProperty("extraMemberIds")
    @get:Convert(SetConverter::class)
    val extraMemberIds : Set<String>

    @get:JsonProperty("name")
    val name : String?

    @get:JsonProperty("ownerId")
    val ownerId : String
}

@Entity
@Table(name = "messages")
@JsonDeserialize(`as` = MessageEntity::class)
interface Message : Persistable, Parcelable {
    @get:JsonIgnore
    @get:Key
    @get:Generated
    val id : Long?

    @get:JsonProperty("localId")
    @get:Column(unique = true)
    val localId : String?

    @get:JsonProperty("remoteId")
    @get:Column(unique = true)
    val remoteId : String?

    @get:Index
    @get:JsonProperty("sendTime")
    val sendTime : Date

    @get:JsonProperty("type")
    val type : String

    @get:JsonProperty("body")
    val body : String?

    @get:JsonProperty("senderId")
    @get:Index
    val senderId : String

    @get:Index
    @get:JsonProperty("roomId")
    val roomId : String
}