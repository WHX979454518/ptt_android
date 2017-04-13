package com.xianzhitech.ptt.util

import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.xianzhitech.ptt.model.Location
import org.slf4j.LoggerFactory
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers

private val logger = LoggerFactory.getLogger("BDLocation")

fun LocationClient.receiveLocation(gpsOnly : Boolean,
                                   intervalMills : Int) : Observable<Location> {
    return Observable.create<Location>({ emitter ->
        val listener: (BDLocation) -> Unit = { emitter.onNext(it.toLocation()) }
        registerLocationListener(listener)
        locOption = LocationClientOption().apply {
            locationMode = if (gpsOnly) LocationClientOption.LocationMode.Device_Sensors else LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
         //   coorType = "gcj02"
            scanSpan = intervalMills
            setNeedDeviceDirect(true)
            isNeedAltitude = true
        }

        emitter.setCancellation {
            AndroidSchedulers.mainThread().createWorker().schedule {
                unRegisterLocationListener(listener)
                stop()
            }
        }

        start()
    }, Emitter.BackpressureMode.LATEST).subscribeOn(AndroidSchedulers.mainThread())
}

fun BaiduMap.receiveMapStatus() : Observable<MapStatus> {
    return Observable.create({ emitter ->
        val listener = object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(p0: MapStatus?) { }

            override fun onMapStatusChange(status: MapStatus) {
                emitter.onNext(status)
            }

            override fun onMapStatusChangeFinish(p0: MapStatus?) { }
        }
        setOnMapStatusChangeListener(listener)
        emitter.setCancellation {
            setOnMapStatusChangeListener(null)
        }
    }, Emitter.BackpressureMode.LATEST)
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