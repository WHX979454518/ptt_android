package com.xianzhitech.ptt.service.handler

import android.content.Context
import com.baidu.location.LocationClient
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.Location
import com.xianzhitech.ptt.service.UserObject
import com.xianzhitech.ptt.util.receiveLocation
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class LocationHandler(private val context : Context,
                      private val signalServiceHandler: SignalServiceHandler) {

    private val locationClient : LocationClient by lazy { LocationClient(context) }

    init {
        signalServiceHandler.currentUserCache
                .distinctUntilChanged()
                .switchMap { user ->
                    (user?.locationScanEnableObservable ?: Observable.empty())
                            .map { enabled -> user to enabled }
                }
                .switchMap { result: Pair<UserObject, Boolean> ->
                    val (user, locationEnabled) = result
                    if (locationEnabled) {
                        //TODO: 网络不通时要把位置记录下来...
                        logger.i { "Start collecting locations every ${user.locationScanInterval} ms" }
                        locationClient.receiveLocation(false, user.locationScanInterval.toInt())
                                .doOnNext { logger.d { "Got location $it" } }
                                .distinctUntilChanged(Location::locationEquals)
                                .buffer(Math.max(1000, Math.max(user.locationReportInterval, user.locationScanInterval)), TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .onErrorReturn {
                    logger.e(it) { "Error saving location" }
                    emptyList()
                }
                .subscribeSimple {
                    logger.i { "Sending ${it.size} locations to server" }
                    signalServiceHandler.sendLocationData(it).subscribeSimple()
                }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("LocationHandler")
    }
}