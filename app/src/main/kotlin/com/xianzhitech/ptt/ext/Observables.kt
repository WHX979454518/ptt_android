package com.xianzhitech.ptt.ext

import rx.Observable

/**
 * Created by fanchao on 17/12/15.
 */

fun <T> Observable<T>.toBlockingFirst() = toBlocking().first()