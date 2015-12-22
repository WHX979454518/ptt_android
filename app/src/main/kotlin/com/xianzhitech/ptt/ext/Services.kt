package com.xianzhitech.ptt.ext

import android.content.*
import android.os.IBinder
import rx.Observable
import rx.Scheduler
import rx.subscriptions.Subscriptions

/**
 * Created by fanchao on 18/12/15.
 */

/**
 * 连接到一个服务, 从Binder获取一个值, 并监听相应的广播获取新的值
 */
fun <T, S> Context.retrieveServiceValue(intent: Intent,
                                        valueRetriever: (S) -> T,
                                        scheduler: Scheduler?,
                                        vararg broadcastActions: String): Observable<T> {
    return connectToService<S>(intent, Context.BIND_AUTO_CREATE).let { if (scheduler != null) it.subscribeOn(scheduler) else it }
            .flatMap { serviceBinder ->
                receiveBroadcasts(*broadcastActions).let { if (scheduler != null) it.subscribeOn(scheduler) else it }
                        .map { valueRetriever(serviceBinder) }
                        .mergeWith(Observable.just(valueRetriever(serviceBinder)))
            }
}

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

        bindService(intent, conn, flags)
        it.add(Subscriptions.create {
            logd("Unbound to service $intent")
            unbindService(conn)
        })
    }
}

fun Context.receiveBroadcasts(vararg actions: String): Observable<Intent> {
    return Observable.create<Intent> { subscriber ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logd("Received $intent for broadcast actions: $actions")
                subscriber.onNext(intent)
            }
        }
        val filter = IntentFilter()
        for (action in actions) {
            filter.addAction(action)
        }
        registerReceiver(receiver, filter)

        subscriber.add(Subscriptions.create {
            logd("Unregistering broadcast actions $actions")
            unregisterReceiver(receiver)
        })
    }
}