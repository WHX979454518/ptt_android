package com.xianzhitech.ptt.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class Location(@SerializedName("lat") val lat : Double,
                    @SerializedName("lng") val lng : Double,
                    @SerializedName("radius") val radius : Int,
                    @SerializedName("alt") val altitude : Int,
                    @SerializedName("speed") val speed : Int,
                    @SerializedName("repTime") val time : Long,
                    @SerializedName("direction") val direction : Float) {

    fun toJSON() : JSONObject {
        return JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("radius", radius)
            put("alt", altitude)
            put("speed", speed)
            put("repTime", time)
            put("direction", direction)
        }
    }

    fun locationEquals(rhs : Location) : Boolean {
        return lat == rhs.lat &&
                lng == rhs.lng &&
                radius == rhs.radius &&
                altitude == rhs.altitude &&
                speed == rhs.speed &&
                direction == rhs.direction
    }
}