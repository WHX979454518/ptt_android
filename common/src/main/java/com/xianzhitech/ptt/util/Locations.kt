package com.xianzhitech.ptt.util

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import android.os.Looper
import com.xianzhitech.ptt.BaseApp
import io.reactivex.Single

object Locations {
    val accurateCriteria = Criteria().apply { accuracy = 50 }

    fun getLocationProvider(criteria: Criteria) : String? {
        val manager = BaseApp.instance.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return manager.getProviders(criteria, true).firstOrNull()
    }

    fun requestLocationUpdate(criteria: Criteria = accurateCriteria): Single<Location> {
        return Single.create { emitter ->
            val manager = BaseApp.instance.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = manager.getProviders(criteria, true).firstOrNull()

            if (provider == null) {
                emitter.onError(LocationProviderNotAvailableException())
                return@create
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    emitter.onSuccess(loc)
                }

                override fun onStatusChanged(provider: String, status: Int, data: Bundle?) {
                    if (status == LocationProvider.OUT_OF_SERVICE) {
                        emitter.onError(LocationProviderNotAvailableException(provider))
                    }
                }

                override fun onProviderEnabled(providerName: String) {
                }

                override fun onProviderDisabled(providerName: String) {
                    emitter.onError(LocationProviderNotAvailableException(providerName))
                }
            }

            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            emitter.setCancellable { manager.removeUpdates(listener) }
        }
    }
}

data class LocationProviderNotAvailableException(val name : String? = null) : RuntimeException("Location provider $name unavailable")