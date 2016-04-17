package com.xianzhitech.ptt.ext

import android.app.Activity
import android.content.*
import android.net.ConnectivityManager
import android.os.IBinder
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.content.LocalBroadcastManager
import android.view.View
import com.xianzhitech.ptt.service.ConnectivityException
import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions

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

fun ConnectivityManager.hasActiveConnection(): Boolean {
    return activeNetworkInfo?.isConnected ?: false
}

fun Context.getActiveNetwork() : Observable<NetworkInfoData?> {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return receiveBroadcasts(false, ConnectivityManager.CONNECTIVITY_ACTION)
            .map { connectivityManager.activeNetworkInfo?.let { NetworkInfoData(it) } }
            .startWith(connectivityManager.activeNetworkInfo?.let { NetworkInfoData(it) })
}

fun Context.getConnectivity(): Observable<Boolean> {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    return Observable.create<Boolean> { subscriber ->
        subscriber.onNext(connectivityManager.hasActiveConnection())

        if (!subscriber.isUnsubscribed) {
            subscriber.add(receiveBroadcasts(false, ConnectivityManager.CONNECTIVITY_ACTION)
                    .subscribe(object : Subscriber<Intent>() {
                        override fun onNext(t: Intent?) {
                            subscriber.onNext(connectivityManager.hasActiveConnection())
                        }

                        override fun onError(e: Throwable?) {
                            subscriber.onError(e)
                        }

                        override fun onCompleted() {
                            subscriber.onCompleted()
                        }
                    }))
        }
    }
}

fun Context.ensureConnectivity(): Observable<Unit> {
    return getConnectivity().first()
            .map {
                if (!it) throw ConnectivityException()
                else Unit
            }
}

val Activity.contentView : View
get() = findViewById(android.R.id.content)

inline fun <reified T : Fragment> FragmentManager.findFragment(tag: String) : T? {
    return findFragmentByTag(tag) as? T
}

fun DialogFragment.dismissImmediately() {
    dismiss()
    fragmentManager.executePendingTransactions()
}