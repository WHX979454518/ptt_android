package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LastLocationByUser @JvmOverloads constructor(
        @get:JsonProperty("userId") val userId: String = "",
        @get:JsonProperty("lat") val lat: Double = 0.0,
        @get:JsonProperty("lng") val lng: Double = 0.0)

