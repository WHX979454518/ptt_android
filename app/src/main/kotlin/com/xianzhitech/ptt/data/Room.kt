package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import io.requery.Entity
import io.requery.Key
import io.requery.Persistable
import io.requery.Table

@Entity
@Table(name = "rooms")
interface Room : Persistable {

    @get:Key
    @get:JsonProperty("idNumber")
    val id : String

    @get:JsonProperty("associatedGroupIds")
    val groups : Set<String>

    @get:JsonProperty("extraMemberIds")
    val extraMembers : Set<String>

    @get:JsonProperty("ownerId")
    val ownerId : String
}