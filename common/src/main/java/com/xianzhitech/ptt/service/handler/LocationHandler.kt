package com.xianzhitech.ptt.service.handler

import android.content.Context
import com.baidu.location.LocationClient
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.util.receiveLocation
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import org.slf4j.LoggerFactory
import org.threeten.bp.Duration
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.TimeUnit

class LocationHandler(private val context : Context,
                      private val signalServiceHandler: SignalBroker) {

    private val locationClient : LocationClient by lazy { LocationClient(context) }

    init {
        signalServiceHandler.currentUser
                .distinctUntilChanged()
                .switchMap { user ->
                    (user.orNull()?.observeLocationScanEnable() ?: Observable.empty())
                            .map { enabled -> user.get() to enabled }
                }
                .flatMap { (user, locationEnabled) ->
                    if (locationEnabled) {
                        //TODO: 网络不通时要把位置记录下来...
                        logger.i { "Start collecting locations every ${user.locationScanIntervalSeconds} ms" }
                        locationClient.receiveLocation(false, TimeUnit.SECONDS.toMillis(user.locationReportIntervalSeconds.toLong()))
                                .doOnNext { logger.d { "Got location $it" } }
                                .onErrorResumeNext(Observable.empty())
                                .buffer(Math.max(1, Math.max(user.locationReportIntervalSeconds, user.locationScanIntervalSeconds)).toLong(),
                                        TimeUnit.SECONDS,
                                        AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .onErrorReturn {
                    logger.e(it) { "Error saving location" }
                    emptyList()
                }
                .subscribe {
                    logger.i { "Sending ${it.size} locations to server" }
                    signalServiceHandler
                            .sendLocationData(it)
                            .retryWhen { err ->
                                err.zipWith(Flowable.range(0, 10), BiFunction { _: Throwable, i : Int -> i })
                                        .switchMap { Flowable.timer(10, TimeUnit.SECONDS, AndroidSchedulers.mainThread()) }
                            }
                            .logErrorAndForget()
                            .subscribe()
                }
    }

    private fun CurrentUser.observeLocationScanEnable() : Observable<Boolean> {
        if (locationEnabled.not()) {
            return Observable.just(false)
        }

        return Observable.defer<Boolean> {
            // Calculate the interval time
            val now = ZonedDateTime.now()

            val reportRanges = (-1..1).map { now.plusDays(it.toLong()) }
                    .filter { locationReportWeekDays.contains(it.dayOfWeek) }
                    .map { it.locationReportRange }

            val currRange = reportRanges.firstOrNull { it.contains(now) }
            val enabled = currRange != null
            val nextCheckTime = currRange?.end ?: (reportRanges.firstOrNull { it.start > now }?.start ?: now.plusHours(1))

            logger.i { "Next check location enable time is $nextCheckTime" }
            Observable.timer(Duration.between(now, nextCheckTime).toMillis(),
                    TimeUnit.MILLISECONDS,
                    AndroidSchedulers.mainThread())
                    .flatMap { Observable.empty<Boolean>() }
                    .startWith(enabled)
        }.repeat()
                .distinctUntilChanged()
    }

    companion object {
        private val logger = LoggerFactory.getLogger("LocationHandler")
    }
}