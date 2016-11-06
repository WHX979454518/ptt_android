package com.xianzhitech.ptt.service

import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.ext.getConnectivity
import com.xianzhitech.ptt.ext.hasActiveConnection
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.receiveBroadcasts
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


private val logger = LoggerFactory.getLogger("RetryPolicy")


interface RetryPolicy {
    fun canContinue(err: Throwable) : Boolean
    fun scheduleNextRetry(): Observable<*>
    fun notifySuccess()
}

class AndroidRetryPolicy(private val context: Context) : RetryPolicy {
    private val currentReconnectInterval = AtomicLong(MIN_RECONNECT_WAIT_MILLS)

    override fun canContinue(err: Throwable): Boolean {
        return true
    }

    override fun scheduleNextRetry(): Observable<*> {
        val interval = currentReconnectInterval.getAndSet(Math.min(MAX_RECONNECT_WAIT_MILLS, (currentReconnectInterval.get() * RECONNECT_INCREASE_FACTOR).toLong()))
        logger.i { "Trying to reconnect $interval ms later" }

        return Observable.amb<Any?>(
                if (context.hasActiveConnection()) {
                    Observable.timer(interval, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                } else {
                    context.getConnectivity(false).first { it }
                },
                context.receiveBroadcasts(false, Intent.ACTION_SCREEN_ON).first()
        )
    }

    override fun notifySuccess() {
        logger.i { "Resetting next reconnect timer to ${MIN_RECONNECT_WAIT_MILLS} ms" }
        currentReconnectInterval.set(MIN_RECONNECT_WAIT_MILLS)
    }

    companion object {
        private val MAX_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS)
        private val MIN_RECONNECT_WAIT_MILLS = 500L
        private val RECONNECT_INCREASE_FACTOR = 1.05f

        private val logger = LoggerFactory.getLogger("AndroidRetryPolicy")
    }
}