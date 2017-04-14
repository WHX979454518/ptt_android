package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.requery.Entity
import io.requery.Key
import io.requery.Persistable
import io.requery.Table

@Entity
@Table(name = "users")
@JsonDeserialize(`as` = UserEntity::class)
interface User : Persistable {
    @get:Key
    @get:JsonProperty("idNumber")
    val id : String

    @get:JsonProperty("name")
    val name : String

    @get:JsonProperty("avatar")
    val avatar : String?

    @get:JsonProperty("priority")
    val priority : Int

    @get:JsonProperty("phoneNumber")
    val phoneNumber : String?

    @get:JsonProperty("enterId")
    val enterpriseId : String

    @get:JsonProperty("enterName")
    val enterpriseName : String

}