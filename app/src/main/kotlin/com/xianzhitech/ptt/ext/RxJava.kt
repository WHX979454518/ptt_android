package com.xianzhitech.ptt.ext

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.functions.Function4
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("RxJava")

fun <T> Observable<T>.logErrorAndForget(extraAction : (err : Throwable) -> Unit = {}) : Observable<T> {
    return onErrorResumeNext{ throwable : Throwable ->
        logger.e(throwable) { "Ignored error: " }
        extraAction(throwable)
        Observable.empty<T>()
    }
}

fun <T> Maybe<T>.logErrorAndForget(extraAction : (err : Throwable) -> Unit = {}) : Maybe<T> {
    return onErrorResumeNext{ throwable : Throwable ->
        logger.e(throwable) { "Ignored error: " }
        extraAction(throwable)
        Maybe.empty<T>()
    }
}


fun Completable.logErrorAndForget(extraAction : (err : Throwable) -> Unit = {}) : Completable {
    return onErrorResumeNext { throwable ->
        logger.e(throwable) { "Ignored error: " }
        extraAction(throwable)
        Completable.complete()
    }
}

fun Completable.doOnLoading(action : (Boolean) -> Unit) : Completable {
    return doOnSubscribe { action(true) }
            .doOnEvent { action(false) }
}

fun <T> Observable<T>.doOnLoading(action : (Boolean) -> Unit) : Observable<T> {
    return doOnSubscribe { action(true) }
            .doOnEach { action(false) }
}

fun <T> Single<T>.doOnLoading(action : (Boolean) -> Unit) : Single<T> {
    return doOnSubscribe { action(true) }
            .doOnEvent { _, _ -> action(false) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T1, T2, R> combineLatest(t1 : Observable<T1>,
                                     t2 : Observable<T2>,
                                     noinline action : (T1, T2) -> R) : Observable<R> {
    return Observable.combineLatest(t1, t2, BiFunction(action))
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T1, T2, T3, R> combineLatest(t1 : Observable<T1>,
                                     t2 : Observable<T2>,
                                     t3 : Observable<T3>,
                                     noinline action : (T1, T2, T3) -> R) : Observable<R> {
    return Observable.combineLatest(t1, t2, t3, Function3(action))
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T1, T2, T3, T4, R> combineLatest(t1 : Observable<T1>,
                                         t2 : Observable<T2>,
                                         t3 : Observable<T3>,
                                         t4 : Observable<T4>,
                                         noinline action : (T1, T2, T3, T4) -> R) : Observable<R> {
    return Observable.combineLatest(t1, t2, t3, t4, Function4(action))
}