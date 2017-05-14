package com.xianzhitech.ptt.util

import com.baidu.location.BDLocation
import com.baidu.location.BDLocationListener
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.data.LatLng
import com.xianzhitech.ptt.data.Location
import com.xianzhitech.ptt.ext.i
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory

object Locations {
    private val logger = LoggerFactory.getLogger("Locations")

    fun requestLocationUpdate(minTimeMills: Long): Observable<Location> {
        logger.i { "Requesting location update with $minTimeMills ms interval" }
        return Observable.create<Location> { emitter ->
            val client = LocationClient(BaseApp.instance, LocationClientOption().apply {
                coorType = "bd09ll"
                isIgnoreKillProcess = true
                enableSimulateGps = BuildConfig.DEBUG
                scanSpan = minTimeMills.toInt()
            })

            val listener = object : BDLocationListener {
                override fun onReceiveLocation(loc: BDLocation) {
                    emitter.onNext(Location(
                            latLng = LatLng(loc.latitude, loc.longitude),
                            radius = loc.radius.toInt(),
                            altitude = loc.altitude.toInt(),
                            speed = loc.speed.toInt(),
                            time = System.currentTimeMillis(),
                            direction = loc.direction
                    ))
                }

                override fun onConnectHotSpotMessage(p0: String?, p1: Int) {
                }
            }

            client.registerLocationListener(listener)
            emitter.setCancellable {
                AndroidSchedulers.mainThread().scheduleDirect {
                    client.stop()
                    client.unRegisterLocationListener(listener)
                }
            }
            client.start()
        }.subscribeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    logger.i { "Got location $it" }
                }
    }

    fun requestSingleLocationUpdate(): Single<Location> {
        logger.i { "Requesting single location" }
        return requestLocationUpdate(1000L).firstOrError()
    }


//    val accurateCriteria = Criteria().apply { accuracy = Criteria.ACCURACY_FINE }
//
//    fun getLocationProvider(criteria: Criteria) : String? {
//        val manager = BaseApp.instance.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//
//        return manager.getProviders(criteria, true).firstOrNull()
//    }
//
//    fun requestLocationUpdate(minTime : Long,
//                              minDistance : Float,
//                              criteria: Criteria = accurateCriteria): Observable<com.xianzhitech.ptt.data.Location> {
//        return Observable.create { emitter ->
//            val manager = BaseApp.instance.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//            val provider = manager.getProviders(criteria, true).firstOrNull()
//
//            if (provider == null) {
//                emitter.onError(LocationProviderNotAvailableException())
//                return@create
//            }
//
//            val listener = object : LocationListener {
//                override fun onLocationChanged(loc: Location) {
//                    emitter.onNext(com.xianzhitech.ptt.data.Location.from(loc))
//                }
//
//                override fun onStatusChanged(provider: String, status: Int, data: Bundle?) {
//                    if (status == LocationProvider.OUT_OF_SERVICE) {
//                        emitter.onError(LocationProviderNotAvailableException(provider))
//                    }
//                }
//
//                override fun onProviderEnabled(providerName: String) {
//                }
//
//                override fun onProviderDisabled(providerName: String) {
//                    emitter.onError(LocationProviderNotAvailableException(providerName))
//                }
//            }
//
//            manager.requestLocationUpdates(provider, minTime, minDistance, listener)
//            emitter.setCancellable { manager.removeUpdates(listener) }
//        }
//    }
//
//
//
//    fun requestSingleLocationUpdate(criteria: Criteria = accurateCriteria): Single<com.xianzhitech.ptt.data.Location> {
//        return Single.create { emitter ->
//            val manager = BaseApp.instance.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//            val provider = manager.getProviders(criteria, true).firstOrNull()
//
//            if (provider == null) {
//                emitter.onError(LocationProviderNotAvailableException())
//                return@create
//            }
//
//            val listener = object : LocationListener {
//                override fun onLocationChanged(loc: Location) {
//                    emitter.onSuccess(com.xianzhitech.ptt.data.Location.from(loc))
//                }
//
//                override fun onStatusChanged(provider: String, status: Int, data: Bundle?) {
//                    if (status == LocationProvider.OUT_OF_SERVICE) {
//                        emitter.onError(LocationProviderNotAvailableException(provider))
//                    }
//                }
//
//                override fun onProviderEnabled(providerName: String) {
//                }
//
//                override fun onProviderDisabled(providerName: String) {
//                    emitter.onError(LocationProviderNotAvailableException(providerName))
//                }
//            }
//
//            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
//            emitter.setCancellable { manager.removeUpdates(listener) }
//        }
//    }
}

data class LocationProviderNotAvailableException(val name: String? = null) : RuntimeException("Location provider $name unavailable")