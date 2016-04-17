package com.xianzhitech.ptt.ext

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.db.ResultSet
import com.xianzhitech.ptt.service.UserDescribableException
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func1
import java.util.*
import java.util.concurrent.TimeoutException

fun <T> Observable<T>.toBlockingFirst() = toBlocking().first()

/**
 * 一个有全局错误处理的Observeable结果处理对象. 如果提供了Context, 错误将通过Toast报告
 */
open class GlobalSubscriber<T>(private val context: Context? = null) : Subscriber<T>() {
    override fun onError(e: Throwable) {
        val message : CharSequence?
        if (context != null) {
            message = when (e) {
                is UserDescribableException -> e.describe(context)
                is TimeoutException -> R.string.error_timeout.toFormattedString(context)
                else -> e.message
            }
        }
        else {
            message = e.message
        }

        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        Log.e("GlobalSubscriber", "Error: ${message}", e)
    }

    override fun onNext(t: T) {
    }

    override fun onCompleted() {
    }
}

fun <T> Observable<ResultSet>.mapToOne(mapper: Func1<ResultSet, T>) = map {
    it.use {
        if (it.moveToFirst()) {
            mapper.call(it)
        }
        else {
            throw NoSuchElementException()
        }
    }
}

fun <T> Observable<ResultSet>.mapToOneOrDefault(mapper: Func1<ResultSet, T>, defaultValue: T?) = map {
    it.use {
        if (it.moveToFirst()) mapper.call(it)
        else defaultValue
    }
}

fun <T> Observable<ResultSet>.mapToList(mapper: Func1<ResultSet, T>): Observable<List<T>> = map {
    it.use {
        ArrayList<T>(it.getCount()).apply {
            while (it.moveToNext()) {
                add(mapper.call(it))
            }
        }
    }
}

fun <T> Observable<T>.subscribeOnOptional(scheduler: Scheduler?): Observable<T> = scheduler?.let { subscribeOn(it) } ?: this

inline fun <T> Observable<T>.subscribeSimple(crossinline action : (T) -> Unit) : Subscription {
    return subscribe(object : GlobalSubscriber<T>() {
        override fun onNext(t: T) {
            action(t)
        }
    })
}

fun <T> Observable<T>.subscribeSimple(context: Context? = null) : Subscription {
    return subscribe(GlobalSubscriber(context))
}


fun <T> Observable<T>.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.subscribeOnMainThread() = subscribeOn(AndroidSchedulers.mainThread())

fun <T> T?.toObservable(): Observable<T> = Observable.just(this)

fun <T, R> Observable<T>.combineWith(other : Observable<R>) = Observable.combineLatest(
        this, other, {first, second -> Pair(first, second)}
)

fun <T> Subscriber<T>.onSingleValue(value: T) {
    onNext(value)
    onCompleted()
}

fun <T> Subscription.addToSubscriber(subscriber: Subscriber<T>) {
    subscriber.add(this)
}
