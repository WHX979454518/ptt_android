package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty


data class Location(@param:JsonProperty("lat") val lat: Double,
                    @param:JsonProperty("lng") val lng: Double,
                    @param:JsonProperty("radius") val radius: Int,
                    @param:JsonProperty("alt") val altitude: Int,
                    @param:JsonProperty("speed") val speed: Int,
                    @param:JsonProperty("repTime") val time: Long,
                    @param:JsonProperty("direction") val direction: Float)