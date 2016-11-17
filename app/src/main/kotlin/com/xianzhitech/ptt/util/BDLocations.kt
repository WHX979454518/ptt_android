package com.xianzhitech.ptt.util

import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.xianzhitech.ptt.ext.plusAssign
import com.xianzhitech.ptt.model.Location
import rx.Observable
import rx.subscriptions.Subscriptions
import java.util.*


fun LocationClient.receiveLocation(gpsOnly : Boolean,
                                   intervalMills : Int) : Observable<Location> {
    return Observable.create<Location> { subscriber ->
        val listener: (BDLocation) -> Unit = { subscriber += it.toLocation() }
        registerLocationListener(listener)
        locOption = LocationClientOption().apply {
            locationMode = if (gpsOnly) LocationClientOption.LocationMode.Device_Sensors else LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
            scanSpan = intervalMills
            setNeedDeviceDirect(true)
            isNeedAltitude = true
        }

        subscriber.add(Subscriptions.create {
            unRegisterLocationListener(listener)
            stop()
        })

        start()
    }.onBackpressureLatest()
}


private fun BDLocation.toLocation() : Location {
    return Location(
            lat = latitude,
            lng = longitude,
            radius = radius.toInt(),
            time = Date(time).time,
            altitude = altitude.toInt(),
            speed = speed.toInt()
    )
}