package com.xianzhitech.ptt.ext

import android.databinding.ObservableMap
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RxUtil")


fun <T> Observable<T>.doOnLoadingState(action1: (Boolean) -> Unit): Observable<T> {
    return doOnSubscribe { action1(true) }
            .doOnEach { action1(false) }
}

fun <T> Single<T>.doOnLoadingState(action1: (Boolean) -> Unit): Single<T> {
    return doOnSubscribe { action1(true) }
            .doOnEvent { _, _ -> action1(false) }
}

fun Completable.doOnLoadingState(action1: (Boolean) -> Unit): Completable {
    return doOnSubscribe { action1(true) }
            .doOnEvent { action1(false) }
}

fun <K, V> ObservableMap<K, V>.observe(): Observable<ObservableMap<K, V>> {
    return Observable.create { emitter ->
        val listener = object : ObservableMap.OnMapChangedCallback<ObservableMap<K, V>, K, V>() {
            override fun onMapChanged(p0: ObservableMap<K, V>?, k: K) {
                emitter.onNext(this@observe)
            }
        }

        emitter.onNext(this@observe)
        addOnMapChangedCallback(listener)
        emitter.setCancellable { removeOnMapChangedCallback(listener) }
    }
}