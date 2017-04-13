package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.requery.*
import java.util.*

@Entity
@Table(name = "messages")
interface Message : Persistable {
    @get:Column(unique = true)
    @get:JsonProperty("localId")
    val localId : String?

    @get:Column(unique = true)
    @get:JsonProperty("remoteId")
    val remoteId : String?

    @get:JsonProperty("sendTime")
    val sendTime : Date

    @get:JsonProperty("type")
    val type : String

    @get:JsonProperty("body")
    val body : String?

    @get:JsonIgnore
    val hasRead : Boolean

    @get:JsonProperty("senderId")
    val senderId : String

    @get:ManyToOne
    @get:Index
    @get:ForeignKey(references = Room::class)
    val roomId : String
}