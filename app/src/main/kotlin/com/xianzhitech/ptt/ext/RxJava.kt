package com.xianzhitech.ptt.ext

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
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