package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.requery.*

@Entity
@Table(name = "groups")
@JsonDeserialize(`as` = GroupEntity::class)
interface Group : Persistable {
    @get:Key
    @get:JsonProperty("idNumber")
    val id : String

    @get:JsonProperty("name")
    val name : String

    @get:JsonProperty("avatar")
    val avatar : String?

    @get:JsonProperty("members")
    val memberIds : Set<String>
}