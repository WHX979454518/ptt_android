package com.xianzhitech.ptt.ext

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("RxJava")

fun <T> Observable<T>.logErrorAndForget(extraAction : () -> Unit = {}) : Observable<T> {
    return doOnError { throwable ->
        logger.e(throwable) { "Ignored error" }
        extraAction()
    }
}

fun <T> Single<T>.logErrorAndForget(extraAction : () -> Unit = {}) : Single<T> {
    return doOnError { throwable ->
        logger.e(throwable) { "Ignored error" }
        extraAction()
    }
}

fun <T> Maybe<T>.logErrorAndForget(extraAction : () -> Unit = {}) : Maybe<T> {
    return doOnError { throwable ->
        logger.e(throwable) { "Ignored error" }
        extraAction()
    }
}

fun Completable.logErrorAndForget(extraAction : () -> Unit = {}) : Completable {
    return doOnError { throwable ->
        logger.e(throwable) { "Ignored error" }
        extraAction()
    }
}