package com.xianzhitech.ptt.service.handler

import android.content.Context
import com.baidu.location.LocationClient
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.subscribeSimple
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
                .distinctUntilChanged { user : UserObject? -> user?.let { Triple(it.locationEnabled, it.locationScanInterval, it.locationReportInterval) } }
                .switchMap { user : UserObject? ->
                    if (user != null) {
                        logger.i { "Start collecting locations every ${user.locationScanInterval} ms" }
                        locationClient.receiveLocation(false, user.locationScanInterval.toInt())
                                .buffer(user.locationReportInterval, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    }
                    else {
                        Observable.never()
                    }
                }
                .flatMap {
                    logger.i { "Sending ${it.size} locations to server" }
                    signalServiceHandler.sendLocationData(it).toObservable<Unit>()
                }
                .onErrorResumeNext(Observable.never())
                .subscribeSimple()
    }

    companion object {
        private val logger = LoggerFactory.getLogger("LocationHandler")
    }
}