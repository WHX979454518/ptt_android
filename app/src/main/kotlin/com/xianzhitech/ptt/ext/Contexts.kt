package com.xianzhitech.ptt.ext

import android.content.*
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import rx.Observable
import rx.Scheduler
import rx.subscriptions.Subscriptions
import kotlin.collections.forEach

/**
 * Created by fanchao on 30/12/15.
 */

fun Context.sendLocalBroadcast(intent: Intent): Unit {
    logd("Sending broadcast $intent")
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
}

fun Context.registerLocalBroadcastReceiver(receiver: BroadcastReceiver, intentFilter: IntentFilter) =
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter)

fun Context.unregisterLocalBroadcastReceiver(receiver: BroadcastReceiver) =
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

fun Context.receiveBroadcasts(useLocalBroadcast: Boolean, vararg actions: String): Observable<Intent> {
    return Observable.create<Intent> { subscriber ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logd("Received $intent for broadcast actions: ${actions.toSqlSet()}")
                subscriber.onNext(intent)
            }
        }

        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        logd("Registering broadcast actions ${actions.toSqlSet()}")
        if (useLocalBroadcast) {
            registerLocalBroadcastReceiver(receiver, filter)
        } else {
            registerReceiver(receiver, filter)
        }

        subscriber.add(Subscriptions.create {
            logd("Unregistering broadcast actions ${actions.toSqlSet()}")
            if (useLocalBroadcast) {
                unregisterLocalBroadcastReceiver(receiver)
            } else {
                unregisterReceiver(receiver)
            }
        })
    }
}

/**
 * 连接到一个服务, 从Binder获取一个值, 并监听相应的广播获取新的值
 */
fun <T, S> Context.retrieveServiceValue(intent: Intent,
                                        valueRetriever: (S) -> T,
                                        useLocalBroadcast: Boolean,
                                        scheduler: Scheduler?,
                                        vararg broadcastActions: String) =
        Observable.combineLatest(
                connectToService<S>(intent, Context.BIND_AUTO_CREATE).subscribeOnOptional(scheduler),
                receiveBroadcasts(useLocalBroadcast, *broadcastActions).subscribeOnOptional(scheduler).mergeWith(null.toObservable()),
                { serviceBinder, intent -> serviceBinder })
                .map { valueRetriever(it) }
                .distinctUntilChanged()

fun <T> Context.connectToService(intent: Intent, flags: Int): Observable<T> {
    return Observable.create {
        val conn = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
            }

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                logd("Bound to service: $service")
                it.onNext(service as T)
            }
        }

        if (bindService(intent, conn, flags)) {
            it.add(Subscriptions.create {
                logd("Unbound to service $intent")
                unbindService(conn)
            })
        } else {
            it.onError(RuntimeException("Can't connect to service"))
        }
    }
}

