package com.xianzhitech.ptt.model

import com.google.gson.annotations.SerializedName

data class Location(@SerializedName("lat") val lat : Double,
                    @SerializedName("lng") val lng : Double,
                    @SerializedName("radius") val radius : Int,
                    @SerializedName("alt") val altitude : Int,
                    @SerializedName("speed") val speed : Int,
                    @SerializedName("repTime") val time : Long)