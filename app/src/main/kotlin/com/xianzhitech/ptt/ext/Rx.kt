package com.xianzhitech.ptt.ext

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.maintain.service.describeInHumanMessage
import org.slf4j.LoggerFactory
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.exceptions.OnErrorNotImplementedException
import rx.functions.Action0
import rx.functions.Action1
import rx.plugins.RxJavaHooks
import rx.subscriptions.Subscriptions
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("RxUtil")

val defaultOnErrorAction: Action1<Throwable> = Action1 {
    logger.e(it) { "Error occurred: ${it.message}" }
    Toast.makeText(App.instance, it.describeInHumanMessage(App.instance), Toast.LENGTH_LONG).show()
}

val defaultOnValueAction: Action1<Any?> = Action1 {  }
val defaultOnCompleteAction: Action0 = Action0 {  }

fun <T> Single<T>.observeOnMainThread(): Single<T> = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.observeOnMainThread() : Observable<T> = observeOn(AndroidSchedulers.mainThread())
fun Completable.observeOnMainThread() : Completable = observeOn(AndroidSchedulers.mainThread())

fun <T> Single<T>.subscribeSimple() : Subscription = subscribe(defaultOnValueAction, defaultOnErrorAction)
fun <T> Single<T>.subscribeSimple(onSuccess : Action1<T>) : Subscription = subscribe(onSuccess, defaultOnErrorAction)
inline fun <T> Single<T>.subscribeSimple(crossinline onSuccess : (T) -> Unit) : Subscription = subscribe(Action1 { onSuccess(it) }, defaultOnErrorAction)

fun <T> Observable<T>.subscribeSimple() : Subscription = subscribe()
fun <T> Observable<T>.subscribeSimple(onNext : Action1<T>) : Subscription = subscribe(onNext, defaultOnErrorAction, defaultOnCompleteAction)
fun <T> Observable<T>.subscribeSimple(onNext : (T) -> Unit) : Subscription = subscribe(Action1 { onNext(it) }, defaultOnErrorAction, defaultOnCompleteAction)

fun Completable.subscribeSimple() : Subscription = subscribe(defaultOnCompleteAction, defaultOnErrorAction)
fun Completable.subscribeSimple(onCompleted : Action0) : Subscription = subscribe(onCompleted, defaultOnErrorAction)
inline fun Completable.subscribeSimple(crossinline onCompleted : () -> Unit) : Subscription = subscribe(Action0 { onCompleted() }, defaultOnErrorAction)

fun <T> Observable<T>.s(onNext: Action1<T>, onError : Action1<Throwable> = defaultOnErrorAction, onCompleted: Action0 = defaultOnCompleteAction) : Subscription {
    return subscribe(onNext, onError, onCompleted)
}

infix operator fun <T> Observer<T>.plusAssign(obj: T) {
    onNext(obj)
}

private class MainThreadSubscription(private val action: Action0,
                                     private val handler : Handler?) : Runnable, Subscription {
    @Volatile private var unsubscribed : Boolean = false

    override fun run() {
        try {
            action.call()
        } catch (e: Throwable) {
            // nothing to do but print a System error as this is fatal and there is nowhere else to throw this
            val ie: IllegalStateException
            if (e is OnErrorNotImplementedException) {
                ie = IllegalStateException("Exception thrown on Scheduler.Worker thread. Add `onError` handling.", e)
            } else {
                ie = IllegalStateException("Fatal Exception thrown on Scheduler.Worker thread.", e)
            }
            RxJavaHooks.onError(ie)
            val thread = Thread.currentThread()
            thread.uncaughtExceptionHandler.uncaughtException(thread, ie)
        }

    }

    override fun isUnsubscribed(): Boolean {
        return unsubscribed
    }

    override fun unsubscribe() {
        unsubscribed = true
        handler?.removeCallbacks(this)
    }
}

class ImmediateMainThreadScheduler : Scheduler() {
    private val handler = Handler(Looper.getMainLooper())

    override fun createWorker(): Worker {
        return object : Worker() {
            @Volatile private var unsubscribed : Boolean = false

            override fun schedule(action: Action0): Subscription {
                if (unsubscribed) {
                    return Subscriptions.unsubscribed()
                }

                if (Looper.myLooper() == Looper.getMainLooper()) {
                    return MainThreadSubscription(action, null).apply { run() }
                }
                else {
                    return post(action, 0, TimeUnit.MILLISECONDS)
                }
            }

            override fun schedule(action: Action0, delayTime: Long, unit: TimeUnit): Subscription {
                if (unsubscribed) {
                    return Subscriptions.unsubscribed()
                }

                return post(action, delayTime, unit)
            }

            private fun post(action: Action0, delayTime: Long, unit: TimeUnit) : Subscription {
                val subscription = MainThreadSubscription(action, handler)
                val msg = Message.obtain(handler, subscription)
                msg.obj = this
                handler.sendMessageDelayed(msg, TimeUnit.MILLISECONDS.convert(delayTime, unit))
                if (unsubscribed) {
                    subscription.unsubscribe()
                }
                return subscription
            }

            override fun isUnsubscribed() = unsubscribed

            override fun unsubscribe() {
                unsubscribed = true
                handler.removeCallbacksAndMessages(this)
            }
        }
    }
}