package com.xianzhitech.ptt.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class Location(@SerializedName("lat") val lat : Double,
                    @SerializedName("lng") val lng : Double,
                    @SerializedName("radius") val radius : Int,
                    @SerializedName("alt") val altitude : Int,
                    @SerializedName("speed") val speed : Int,
                    @SerializedName("repTime") val time : Long) {

    fun toJSON() : JSONObject {
        return JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("radius", radius)
            put("alt", altitude)
            put("speed", speed)
            put("repTime", time)
        }
    }
}