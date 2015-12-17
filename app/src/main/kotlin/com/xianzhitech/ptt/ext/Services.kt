package com.xianzhitech.ptt.ext

import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.ui.util.RxUtil
import rx.Observable

/**
 * Created by fanchao on 18/12/15.
 */

/**
 * 连接到一个服务, 从Binder获取一个值, 并监听相应的广播获取新的值
 */
fun <T, S> Context.retrieveServiceValue(intent: Intent,
                                        valueRetriever: (S) -> T,
                                        vararg broadcastActions: String): Observable<T> {
    return RxUtil.fromService<S>(this, intent, Context.BIND_AUTO_CREATE)
            .flatMap { serviceBinder ->
                RxUtil.fromBroadcast(this, *broadcastActions)
                        .map { valueRetriever(serviceBinder) }
                        .mergeWith(Observable.just(valueRetriever(serviceBinder)))
            }
}