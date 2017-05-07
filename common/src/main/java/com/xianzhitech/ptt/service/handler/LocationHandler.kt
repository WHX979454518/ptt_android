package com.xianzhitech.ptt.service.handler

import android.content.Context
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.util.Locations
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.slf4j.LoggerFactory
import org.threeten.bp.Duration
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.TimeUnit

class LocationHandler(appComponent: AppComponent) {

    init {
        appComponent.signalBroker.currentUser
                .distinctUntilChanged()
                .switchMap { user ->
                    (user.orNull()?.observeLocationScanEnable() ?: Observable.empty())
                            .map { enabled -> user.get() to enabled }
                }
                .switchMap { (user, locationEnabled) ->
                    if (locationEnabled) {
                        logger.i { "Start collecting locations every ${user.locationScanIntervalSeconds} ms" }
                        Locations.requestLocationUpdate(TimeUnit.SECONDS.toMillis(Math.max(1L, user.locationReportIntervalSeconds.toLong())), 10f)
                                .doOnNext {
                                    logger.d { "Got location $it" }
                                    appComponent.storage.savePendingLocations(listOf(it))
                                            .logErrorAndForget()
                                            .subscribe()
                                }
                    } else {
                        Observable.never()
                    }
                }
                .logErrorAndForget()
                .subscribe()


        appComponent.signalBroker.connectionState
                .map { it == SignalApi.ConnectionState.CONNECTED }
                .distinctUntilChanged()
                .switchMap { connected ->
                    if (connected) {
                        appComponent.storage.getPendingLocations()
                                .flatMap { locations ->
                                    appComponent.signalBroker.sendLocationData(locations)
                                            .andThen(appComponent.storage.removePendingLocations(locations))
                                            .andThen(Observable.empty<Unit>())
                                }
                    } else {
                        Observable.empty<Unit>()
                    }
                }
                .logErrorAndForget()
                .subscribe()
    }

    private fun CurrentUser.observeLocationScanEnable(): Observable<Boolean> {
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