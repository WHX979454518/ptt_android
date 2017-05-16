package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.xianzhitech.ptt.data.LatLng
import com.xianzhitech.ptt.data.User


data class LastLocationByUser @JvmOverloads constructor(@get:JsonProperty("userId") val userId: String = "",
                                                        @get:JsonUnwrapped val latLng: LatLng = LatLng.EMPTY,
                                                        @get:JsonProperty("repTime") val time: String? = null) {

    @get:JsonIgnore
    var user: User? = null
}
