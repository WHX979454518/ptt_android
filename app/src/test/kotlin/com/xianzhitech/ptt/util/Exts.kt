package com.xianzhitech.ptt.util

import rx.Observable
import rx.observers.TestSubscriber
import java.util.concurrent.TimeUnit

/**
 * Created by fanchao on 20/01/16.
 */
fun <T> Observable<T>.test() = TestSubscriber<T>().apply {
    subscribe(this)
    awaitTerminalEventAndUnsubscribeOnTimeout(5, TimeUnit.SECONDS)
    assertNoErrors()
}

fun <T> Observable<T>.test(func : (TestSubscriber<T>) -> Any) {
    test().apply { func(this) }
}