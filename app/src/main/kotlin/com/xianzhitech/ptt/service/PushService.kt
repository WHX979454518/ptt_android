package com.xianzhitech.ptt.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xianzhitech.ptt.ext.getConnectivity
import com.xianzhitech.ptt.ext.hasActiveConnection
import com.xianzhitech.ptt.ext.logtagd
import com.xianzhitech.ptt.ext.observeOnMainThread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func1
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.io.IOException
import java.util.concurrent.TimeUnit


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
            logtagd("PushService", "Uri hasn't changed. Skip new connection")
            return
        }

        currUri = uri
        messageSubscription = receiveWebSocketMessage(this,
                OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build(),
                uri,
                ReconnectPolicy(applicationContext))
            .observeOnMainThread()
            .subscribe {
                logtagd("PushService", "Received message $it")
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

private class ReconnectPolicy(private val context: Context) : Func1<Throwable?, Observable<*>> {
    private var currentReconnectInterval = 0L

    private fun retryInterval() : Long {
        return synchronized(this, {
            currentReconnectInterval = Math.min(MAX_RECONNECT_WAIT_MILLS, Math.max(MIN_RECONNECT_WAIT_MILLS, (currentReconnectInterval * RECONNECT_INCREASE_FACTOR).toLong()))
            currentReconnectInterval
        })
    }

    override fun call(t: Throwable?): Observable<*> {
        return if (context.hasActiveConnection()) {
            Observable.timer(retryInterval(), TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
        } else {
            resetRetryInterval()
            context.getConnectivity(false).filter { it }.first()
        }
    }

    private fun resetRetryInterval() {
        synchronized(this, { currentReconnectInterval = 0L })
    }
}

private fun receiveWebSocketMessage(context: Context,
                                    httpClient: OkHttpClient,
                                    uri: Uri,
                                    retryPolicy : Func1<Throwable?, Observable<*>>) : Observable<String> {
    return Observable.create<String> { subscriber ->
        subscriber.logtagd("PushService", "Connecting to $uri")
        val client = WebSocketCall.create(httpClient, Request.Builder().url(uri.toString()).build())
        client.enqueue(object : WebSocketListener {
            override fun onOpen(webSocket: WebSocket?, response: Response?) {
                logtagd("PushService", "Connected to $uri")
            }

            override fun onPong(payload: Buffer?) { }

            override fun onClose(code: Int, reason: String?) {
                logtagd("PushService", "Communication to $uri closed: code = $code, reason = $reason")
            }

            override fun onFailure(e: IOException?, response: Response?) {
                logtagd("PushService", "Error when communicating with $uri: $e")
                subscriber.onError(e)
            }

            override fun onMessage(message: ResponseBody) {
                subscriber.onNext(message.string())
            }
        })

        subscriber.add(context.getConnectivity(false).subscribe {
            if (it.not()) {
                subscriber.onError(RuntimeException("No internet connection"))
            }
        })

        subscriber.add(Subscriptions.create {
            client.cancel()
        })

    }.subscribeOn(Schedulers.io())
    .retryWhen { it.switchMap(retryPolicy) }
}