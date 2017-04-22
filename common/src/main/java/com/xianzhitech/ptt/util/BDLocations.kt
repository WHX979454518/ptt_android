package com.xianzhitech.ptt.util

import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.xianzhitech.ptt.data.Location
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BDLocation")

fun LocationClient.receiveLocation(gpsOnly : Boolean,
                                   intervalMills : Long) : Observable<Location> {
    return Observable.create<Location> { emitter ->
        val listener: (BDLocation) -> Unit = { emitter.onNext(it.toLocation()) }
        registerLocationListener(listener)
        locOption = LocationClientOption().apply {
            locationMode = if (gpsOnly) LocationClientOption.LocationMode.Device_Sensors else LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
         //   coorType = "gcj02"
            scanSpan = intervalMills.toInt()
            setNeedDeviceDirect(true)
            isNeedAltitude = true
        }

        emitter.setCancellable {
            AndroidSchedulers.mainThread().scheduleDirect {
                unRegisterLocationListener(listener)
                stop()
            }
        }

        start()
    }.subscribeOn(AndroidSchedulers.mainThread())
}

fun BaiduMap.receiveMapStatus() : Observable<MapStatus> {
    return Observable.create { emitter ->
        val listener = object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(p0: MapStatus?) { }

            override fun onMapStatusChange(status: MapStatus) {
                emitter.onNext(status)
            }

            override fun onMapStatusChangeFinish(p0: MapStatus?) { }
        }
        setOnMapStatusChangeListener(listener)
        emitter.setCancellable {
            setOnMapStatusChangeListener(null)
        }
    }
}

private fun BDLocation.toLocation() : Location {
    return Location(
            lat = latitude,
            lng = longitude,
            radius = radius.toInt(),
            time = System.currentTimeMillis(),
            altitude = altitude.toInt(),
            speed = speed.toInt(),
            direction = direction
    )
}