package com.xianzhitech.ptt.ext

import org.slf4j.LoggerFactory
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action0
import rx.functions.Action1

private val logger = LoggerFactory.getLogger("RxUtil")

val defaultOnErrorAction: Action1<Throwable> = Action1 { logger.e(it) { "Error occurred: ${it?.message}" } }
val defaultOnValueAction: Action1<Any?> = Action1 { logger.d { "Got unhandled success value: $it" } }
val defaultOnCompleteAction: Action0 = Action0 { logger.d { "Got ignored complete signal" } }

fun <T> Single<T>.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())
fun Completable.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())

fun <T> Single<T>.subscribeSimple() = subscribe(defaultOnValueAction, defaultOnErrorAction)
fun <T> Single<T>.subscribeSimple(onSuccess : Action1<T>) = subscribe(onSuccess, defaultOnErrorAction)
inline fun <T> Single<T>.subscribeSimple(crossinline onSuccess : (T) -> Unit) = subscribe(Action1 { onSuccess(it) }, defaultOnErrorAction)

fun <T> Observable<T>.subscribeSimple() = subscribe()
fun <T> Observable<T>.subscribeSimple(onNext : Action1<T>) = subscribe(onNext, defaultOnErrorAction, defaultOnCompleteAction)
fun <T> Observable<T>.subscribeSimple(onNext : (T) -> Unit) = subscribe(Action1 { onNext(it) }, defaultOnErrorAction, defaultOnCompleteAction)

fun Completable.subscribeSimple() = subscribe(defaultOnCompleteAction, defaultOnErrorAction)
fun Completable.subscribeSimple(onCompleted : Action0) = subscribe(onCompleted, defaultOnErrorAction)
inline fun Completable.subscribeSimple(crossinline onCompleted : () -> Unit) = subscribe(Action0 { onCompleted() }, defaultOnErrorAction)

fun <T> Observable<T>.s(onNext: Action1<T>, onError : Action1<Throwable> = defaultOnErrorAction, onCompleted: Action0 = defaultOnCompleteAction) : Subscription {
    return subscribe(onNext, onError, onCompleted)
}

infix operator fun <T> Observer<T>.plusAssign(obj: T) {
    onNext(obj)
}

fun <T> Single<T>.toCompletable() : Completable {
    return Completable.fromSingle(this)
}