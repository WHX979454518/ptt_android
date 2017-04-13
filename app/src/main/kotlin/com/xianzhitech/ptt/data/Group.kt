package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import io.requery.Entity
import io.requery.Key
import io.requery.Persistable
import io.requery.Table

@Entity
@Table(name = "groups")
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