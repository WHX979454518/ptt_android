package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.xianzhitech.ptt.data.Location
import com.xianzhitech.ptt.data.User


data class UserLocation @JvmOverloads constructor(@get:JsonProperty("userId") val userId: String = "",
                                                  @get:JsonUnwrapped val location: Location = Location.EMPTY) {

    @get:JsonIgnore
    var user: User? = null
}