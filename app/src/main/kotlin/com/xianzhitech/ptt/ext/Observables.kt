package com.xianzhitech.ptt.ext

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.xianzhitech.ptt.service.describeInHumanMessage
import rx.*
import rx.android.schedulers.AndroidSchedulers


/**
 * 一个有全局错误处理的Observeable结果处理对象. 如果提供了Context, 错误将通过Toast报告
 */
open class GlobalSubscriber<T>(private val context: Context? = null) : Subscriber<T>() {
    override fun onError(e: Throwable) {
        globalHandleError(e, context)
    }

    override fun onNext(t: T) {
    }

    override fun onCompleted() {
    }
}

fun globalHandleError(e: Throwable, context: Context? = null) {
    val message: CharSequence?
    if (context != null) {
        message = e.describeInHumanMessage(context)
    } else {
        message = e.message
    }

    if (context != null) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Log.e("GlobalSubscriber", "Error: $message", e)
}

fun <T> Observable<T>.subscribeSimple(action: (T) -> Unit): Subscription {
    return subscribe(object : GlobalSubscriber<T>() {
        override fun onNext(t: T) {
            action(t)
        }
    })
}

fun <T> Observable<T>.subscribeSimple(context: Context? = null): Subscription {
    return subscribe(GlobalSubscriber(context))
}

fun Completable.subscribeSimple(action: () -> Unit): Subscription {
    return subscribe({ err ->
        Log.e("GlobalSubscriber", err?.message, err)
    }, action)
}

fun Completable.subscribeSimple(): Subscription {
    return subscribeSimple { }
}

fun <T> Single<T>.subscribeSimple(): Subscription {
    return subscribeSimple {}
}

fun <T> Single<T>.subscribeSimple(action: (T) -> Unit): Subscription {
    return subscribe(object : GlobalSubscriber<T>() {
        override fun onNext(t: T) {
            action(t)
        }
    })
}


fun <T> Observable<T>.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.subscribeOnMainThread() = subscribeOn(AndroidSchedulers.mainThread())

fun <T> T?.toObservable(): Observable<T> = Observable.just(this)

fun <T, R> Observable<T>.combineWith(other: Observable<R>) = Observable.combineLatest(
        this, other, { first, second -> Pair(first, second) }
)

fun <T> Subscriber<T>.onSingleValue(value: T) {
    onNext(value)
    onCompleted()
}

fun <T> Subscription.addToSubscriber(subscriber: Subscriber<T>) {
    subscriber.add(this)
}

operator fun <T> Observer<T>.plusAssign(obj: T?) {
    onNext(obj)
}