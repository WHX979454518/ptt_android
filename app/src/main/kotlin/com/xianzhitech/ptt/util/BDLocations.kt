package com.xianzhitech.ptt.util

import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.CoordinateConverter
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.plusAssign
import com.xianzhitech.ptt.model.Location
import com.xianzhitech.ptt.service.dto.NearbyUser
import org.slf4j.LoggerFactory
import rx.AsyncEmitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.Subscriptions
import java.util.*

private val logger = LoggerFactory.getLogger("BDLocation")

fun LocationClient.receiveLocation(gpsOnly : Boolean,
                                   intervalMills : Int) : Observable<Location> {
    return Observable.fromEmitter<Location>({ emitter ->
        val listener: (BDLocation) -> Unit = { emitter.onNext(it.toLocation()) }
        registerLocationListener(listener)
        locOption = LocationClientOption().apply {
            locationMode = if (gpsOnly) LocationClientOption.LocationMode.Device_Sensors else LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
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
    }, AsyncEmitter.BackpressureMode.LATEST).subscribeOn(AndroidSchedulers.mainThread())
}

fun BaiduMap.receiveMapStatus() : Observable<MapStatus> {
    return Observable.fromEmitter({ emitter ->
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
    }, AsyncEmitter.BackpressureMode.LATEST)
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