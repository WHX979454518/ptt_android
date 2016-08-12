package com.xianzhitech.ptt.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import io.socket.client.SocketIOException
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
import java.util.concurrent.TimeoutException


private val logger = LoggerFactory.getLogger("PushService")

class PushService : Service() {
    companion object {
        private const val ACTION_START = "connect"
        private const val ACTION_UPDATE_NOTIFICATION = "update_notification"

        const val ACTION_MESSAGE = "PushService.Message"

        const val EXTRA_MSG = "msg"
        const val EXTRA_SERVER_URI = "server_uri"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_TOKEN = "user_token"

        private const val EXTRA_NOTIFICATION = "notification"

        fun start(context: Context, serverUri : String, userId : String, token : String) {
            context.startService(Intent(context, PushService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_USER_ID, userId)
                    .putExtra(EXTRA_USER_TOKEN, token)
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

    private var connectParams : ConnectParams? = null
    private var messageSubscription : Subscription? = null

    private fun doConnect(connectParams : ConnectParams) {
        if (this.connectParams == connectParams) {
            logger.i { "Uri hasn't changed. Skip new connection" }
            return
        }

        this.connectParams = connectParams
        messageSubscription?.unsubscribe()
        messageSubscription = receivePushService(
                OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build(),
                Func0 {
                    Request.Builder().url(connectParams.uri)
                            .header("X-User-Id", connectParams.userId)
                            .header("X-User-Token", connectParams.userToken)
                            .build()
                },
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
                doConnect(ConnectParams(intent!!.getStringExtra(EXTRA_SERVER_URI),
                        intent.getStringExtra(EXTRA_USER_ID),
                        intent.getStringExtra(EXTRA_USER_TOKEN)))
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
        connectParams = null
        messageSubscription?.unsubscribe()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

private data class ConnectParams(val uri : String,
                                 val userId : String,
                                 val userToken : String)

class AndroidReconnectPolicy(private val context: Context) : Func1<Throwable?, Observable<*>> {
    private var currentReconnectInterval = 0L

    private fun retryInterval() : Long {
        return synchronized(this, {
            currentReconnectInterval = Math.min(MAX_RECONNECT_WAIT_MILLS, Math.max(MIN_RECONNECT_WAIT_MILLS, (currentReconnectInterval * RECONNECT_INCREASE_FACTOR).toLong()))
            currentReconnectInterval
        })
    }

    override fun call(t: Throwable?): Observable<*> {
        val err : Throwable? = when {
            (t is SocketIOException && t.message == "timeout") || t is TimeoutException -> StaticUserException(R.string.error_timeout)
            t is KnownServerException || t is UnknownServerException -> t
            else -> null
        }

        return when {
            err is KnownServerException || err is StaticUserException -> Observable.error<Any>(err)
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

    companion object {
        private val MAX_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)
        private val MIN_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS)
        private val RECONNECT_INCREASE_FACTOR = 1.5f
    }
}


private fun receivePushService(httpClient: OkHttpClient,
                               requestProvider : Func0<Request>,
                               sendMessageProvider : Observable<String>? = null,
                               retryPolicy : Func1<Throwable?, Observable<*>>) : Observable<String> {

    return observable<String> { subscriber ->
        val request = requestProvider.call()
        logger.i {"Connecting to $request" }
        val client = WebSocketCall.create(httpClient, request)
        client.enqueue(object : WebSocketListener {
            override fun onOpen(webSocket: WebSocket, response: Response?) {
                logger.i {"Connected to $request" }
                if (sendMessageProvider != null && subscriber.isUnsubscribed.not()) {
                    subscriber.add(sendMessageProvider.subscribe {  webSocket.sendMessage(RequestBody.create(WebSocket.TEXT, it))  })
                }
            }

            override fun onPong(payload: Buffer?) { }

            override fun onClose(code: Int, reason: String?) {
                logger.i {"Communication to $request closed: code = $code, reason = $reason" }
            }

            override fun onFailure(e: IOException?, response: Response?) {
                logger.i {"Error when communicating with $request: $e" }
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