package com.xianzhitech.ptt.service

import android.app.ActivityManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.ui.RoomInvitationHelperActivity
import okhttp3.*
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func0
import rx.lang.kotlin.add
import rx.lang.kotlin.observable
import rx.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


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

        val am = (applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)

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
                    // 检查主进程是否存在，如果存在，则不需要调用help activity
                    if (am.runningAppProcesses.indexOfFirst { it.processName == applicationContext.packageName } >= 0) {
                        logger.d { "Sending message broadcast" }
                        sendBroadcast(Intent(PushService.ACTION_MESSAGE).putExtra(EXTRA_MSG, it))
                    }
                    else {
                        logger.d { "Starting helper activity to deliver broadcast msg" }
                        startActivity(Intent(this, RoomInvitationHelperActivity::class.java)
                                .putExtra(RoomInvitationHelperActivity.EXTRA_MSG, it)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
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


interface ReconnectPolicy {
    fun scheduleNextConnect() : Single<*>
    fun notifyConnected()
}

class AndroidReconnectPolicy(private val context: Context) : ReconnectPolicy {
    private val currentReconnectInterval = AtomicLong(MIN_RECONNECT_WAIT_MILLS)

    override fun scheduleNextConnect(): Single<*> {
        val interval = currentReconnectInterval.getAndSet(Math.min(MAX_RECONNECT_WAIT_MILLS, (currentReconnectInterval.get() * RECONNECT_INCREASE_FACTOR).toLong()))
        logger.i { "Trying to reconnect $interval ms later" }

        return Observable.amb<Any?>(
                if (context.hasActiveConnection()) {
                    Observable.timer(interval, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                } else {
                    context.getConnectivity(false).first { it }
                },
                context.receiveBroadcasts(false, Intent.ACTION_SCREEN_ON).first()
        ).toSingle()
    }

    override fun notifyConnected() {
        logger.i { "Resetting next reconnect timer to $MIN_RECONNECT_WAIT_MILLS ms" }
        currentReconnectInterval.set(MIN_RECONNECT_WAIT_MILLS)
    }

    companion object {
        private val MAX_RECONNECT_WAIT_MILLS = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)
        private val MIN_RECONNECT_WAIT_MILLS = 500L
        private val RECONNECT_INCREASE_FACTOR = 1.5f

        private val logger = LoggerFactory.getLogger("AndroidReconnectPolicy")
    }
}


private fun receivePushService(httpClient: OkHttpClient,
                               requestProvider : Func0<Request>,
                               sendMessageProvider : Observable<String>? = null,
                               retryPolicy : ReconnectPolicy) : Observable<String> {

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
                retryPolicy.notifyConnected()
            }

            override fun onPong(payload: Buffer?) {
                logger.d { "Received pong" }
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
        it.switchMap {
            retryPolicy.scheduleNextConnect().toObservable()
        }
    }
}