package com.xianzhitech.ptt.service.dto

import com.baidu.mapapi.model.LatLng
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.model.User
import org.json.JSONObject

class NearbyUser(val userId : String,
                 val latLng : LatLng) {
    var user : User? = null

    companion object {
        fun fromJSON(json : JSONObject) : NearbyUser {
            return NearbyUser(userId = json.getString("idNumber"), latLng = LatLng(json.getDouble("lat"), json.getDouble("lng")))
        }
    }
}