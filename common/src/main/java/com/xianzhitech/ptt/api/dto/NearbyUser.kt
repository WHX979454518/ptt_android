package com.xianzhitech.ptt.api.dto

import com.baidu.mapapi.model.LatLng
import com.fasterxml.jackson.annotation.JsonIgnore
import com.xianzhitech.ptt.data.User


class NearbyUser(val userId : String,
                 val latLng: LatLng) {

    @get:JsonIgnore
    var user : User? = null
}