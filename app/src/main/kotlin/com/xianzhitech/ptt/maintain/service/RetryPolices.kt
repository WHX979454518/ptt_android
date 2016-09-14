package com.xianzhitech.ptt.maintain.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.xianzhitech.ptt.ext.*
import okhttp3.*
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func0
import rx.functions.Func1
import rx.lang.kotlin.add
import rx.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


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
        logger.i { "Resetting next reconnect timer to $MIN_RECONNECT_WAIT_MILLS ms" }
        currentReconnectInterval.set(MIN_RECONNECT_WAIT_MILLS)
    }

    companion object {
        private val MAX_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)
        private val MIN_RECONNECT_WAIT_MILLS = 500L
        private val RECONNECT_INCREASE_FACTOR = 1.5f

        private val logger = LoggerFactory.getLogger("AndroidRetryPolicy")
    }
}


private fun receivePushService(httpClient: OkHttpClient,
                               requestProvider : Func0<Request>,
                               powerManager: PowerManager,
                               pingPongScheduler : Scheduler,
                               sendMessageProvider : Observable<String>? = null,
                               retryPolicy : RetryPolicy) : Observable<String> {

    return Observable.create<String> { subscriber ->
        val request = requestProvider.call()
        logger.i {"Connecting to $request" }
        val client = WebSocketCall.create(httpClient, request)

        val timerSubscription = AtomicReference<Subscription>()
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PushService")

        wakeLock.acquire(10000)
        client.enqueue(object : WebSocketListener {
            private lateinit var webSocket : WebSocket

            override fun onOpen(webSocket: WebSocket, response: Response?) {
                logger.i {"Connected to $request" }
                this.webSocket = webSocket
                if (sendMessageProvider != null && subscriber.isUnsubscribed.not()) {
                    subscriber.add(sendMessageProvider.subscribe {  webSocket.sendMessage(RequestBody.create(WebSocket.TEXT, it))  })
                }
                retryPolicy.notifySuccess()
                sendPing()
            }

            private fun sendPing() {
                logger.d { "Sending ping" }
                try {
                    wakeLock.acquire(10000)
                    webSocket.sendPing(null)
                } catch(e: Exception) {
                    logger.e(e) { "Error sending ping" }
                    subscriber.onError(e)
                    webSocket.close(1011, "Error sending ping")
                }
            }

            override fun onPong(payload: Buffer?) {
                logger.d { "Received pong" }
                wakeLock.release()
                restartPingTimer()
            }

            private fun restartPingTimer() {
                timerSubscription.getAndSet(
                        Observable.timer(2, TimeUnit.MINUTES, pingPongScheduler)
                                .switchMap {
                                    sendPing()
                                    Observable.timer(1, TimeUnit.MINUTES, AndroidSchedulers.mainThread())
                                }
                                .subscribe {
                                    logger.i { "Ping timeout" }
                                    subscriber.onError(TimeoutException("Ping timeout"))
                                }.apply { subscriber.add(this) })
                        ?.unsubscribe()
            }

            override fun onClose(code: Int, reason: String?) {
                logger.i {"Communication to $request closed: code = $code, reason = $reason" }
            }

            override fun onFailure(e: IOException?, response: Response?) {
                logger.i {"Error when communicating with $request: $e" }
                subscriber.onError(e)
            }

            override fun onMessage(message: ResponseBody) {
                try {
                    val msg = message.string()
                    logger.d { "Received message $msg" }
                    subscriber.onNext(msg)
                } catch(e: Exception) {
                    logger.e(e) { "Error reading message" }
                    subscriber.onError(e)
                }
            }
        })

        subscriber.add { client.cancel() }
    }.subscribeOn(Schedulers.io()).retryWhen {
        it.switchMap<Any>(Func1 {
            if (retryPolicy.canContinue(it).not()) {
                Observable.error(it)
            }
            else {
                retryPolicy.scheduleNextRetry()
            }
        })
    }
}