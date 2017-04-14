package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.requery.*

@Entity
@Table(name = "rooms")
@JsonDeserialize(`as` = RoomEntity::class)
interface Room : Persistable {

    @get:Key
    @get:JsonProperty("idNumber")
    val id: String

    @get:JsonProperty("associatedGroupIds")
    val groups: Set<String>

    @get:JsonProperty("extraMemberIds")
    val extraMembers: Set<String>

    @get:JsonProperty("ownerId")
    val ownerId: String
}