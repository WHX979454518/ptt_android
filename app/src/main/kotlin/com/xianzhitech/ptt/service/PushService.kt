package com.xianzhitech.ptt.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xianzhitech.ptt.ext.*
import okhttp3.*
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func0
import rx.functions.Func1
import rx.lang.kotlin.add
import rx.lang.kotlin.observable
import rx.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.TimeUnit


private val logger = LoggerFactory.getLogger("PushService")

class PushService : Service() {
    companion object {
        private const val ACTION_START = "connect"
        private const val ACTION_UPDATE_NOTIFICATION = "update_notification"

        const val ACTION_MESSAGE = "PushService.Message"

        const val EXTRA_MSG = "msg"
        const val EXTRA_SERVER_URI = "server_uri"

        private const val EXTRA_NOTIFICATION = "notification"

        fun start(context: Context, serverUri : Uri) {
            context.startService(Intent(context, PushService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SERVER_URI, serverUri)
            )
        }

        fun update(context: Context, notification: Notification) {
            context.startService(Intent(context, PushService::class.java)
                    .setAction(ACTION_UPDATE_NOTIFICATION)
                    .putExtra(EXTRA_NOTIFICATION, notification)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PushService::class.java))
        }
    }

    private var currUri : Uri? = null
    private var messageSubscription : Subscription? = null

    private fun doConnect(uri : Uri) {
        if (uri == currUri) {
            logger.i { "Uri hasn't changed. Skip new connection" }
            return
        }

        currUri = uri
        messageSubscription = receivePushService(
                OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build(),
                Func0 { Request.Builder().url(uri.toString()).build() },
                null,
                AndroidReconnectPolicy(applicationContext))
            .observeOnMainThread()
            .subscribe {
                logger.i { "Received message $it" }
                sendBroadcast(Intent(ACTION_MESSAGE).putExtra(EXTRA_MSG, it))
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                doConnect(intent!!.getParcelableExtra(EXTRA_SERVER_URI))
                START_REDELIVER_INTENT
            }

            ACTION_UPDATE_NOTIFICATION -> {
                startForeground(1, intent!!.getParcelableExtra(EXTRA_NOTIFICATION))
                START_REDELIVER_INTENT
            }

            else -> super.onStartCommand(intent, flags, startId)
        }
    }

    override fun onDestroy() {
        stopForeground(true)
        currUri = null
        messageSubscription?.unsubscribe()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

private val MAX_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)
private val MIN_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS)
private val RECONNECT_INCREASE_FACTOR = 1.5f

class AndroidReconnectPolicy(private val context: Context) : Func1<Throwable?, Observable<*>> {
    private var currentReconnectInterval = 0L

    private fun retryInterval() : Long {
        return synchronized(this, {
            currentReconnectInterval = Math.min(MAX_RECONNECT_WAIT_MILLS, Math.max(MIN_RECONNECT_WAIT_MILLS, (currentReconnectInterval * RECONNECT_INCREASE_FACTOR).toLong()))
            currentReconnectInterval
        })
    }

    override fun call(t: Throwable?): Observable<*> {
        return when {
            t is KnownServerException -> Observable.error<Any>(t)
            context.hasActiveConnection() -> Observable.timer(retryInterval(), TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            else -> {
                resetRetryInterval()
                context.getConnectivity(false).filter { it }.first()
            }
        }
    }

    private fun resetRetryInterval() {
        synchronized(this, { currentReconnectInterval = 0L })
    }
}



private fun receivePushService(httpClient: OkHttpClient,
                               requestProvider : Func0<Request>,
                               sendMessageProvider : Observable<String>? = null,
                               retryPolicy : Func1<Throwable?, Observable<*>>) : Observable<String> {

    return observable<String> { subscriber ->
        val request = requestProvider.call()
        val uri = request.url()
        logger.i {"Connecting to $uri" }
        val client = WebSocketCall.create(httpClient, request)
        client.enqueue(object : WebSocketListener {
            override fun onOpen(webSocket: WebSocket, response: Response?) {
                logger.i {"Connected to $uri" }
                if (sendMessageProvider != null && subscriber.isUnsubscribed.not()) {
                    subscriber.add(sendMessageProvider.subscribe {  webSocket.sendMessage(RequestBody.create(WebSocket.TEXT, it))  })
                }
            }

            override fun onPong(payload: Buffer?) { }

            override fun onClose(code: Int, reason: String?) {
                logger.i {"Communication to $uri closed: code = $code, reason = $reason" }
            }

            override fun onFailure(e: IOException?, response: Response?) {
                logger.i {"Error when communicating with $uri: $e" }
                subscriber.onError(e)
            }

            override fun onMessage(message: ResponseBody) {
                logger.d { "Received message ${message.string()}" }
                subscriber.onNext(message.string())
            }
        })

        subscriber.add { client.cancel() }
    }.subscribeOn(Schedulers.io()).retryWhen { it.switchMap(retryPolicy) }
}