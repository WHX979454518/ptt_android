package com.xianzhitech.ptt.service.dto

import com.baidu.mapapi.model.LatLng
import com.xianzhitech.ptt.model.User

class NearbyUser(val userId : String,
                 val latLng : LatLng) {
    var user : User? = null
}