package com.xianzhitech.ptt.ext

import android.databinding.ObservableField
import rx.Observable
import rx.lang.kotlin.add
import java.util.concurrent.atomic.AtomicInteger


fun <T> ObservableField<T>.toRx(): Observable<T> {
    return Observable.create { emitter ->
        emitter.onNext(get())

        if (emitter.isUnsubscribed.not()) {
            val callback = object : android.databinding.Observable.OnPropertyChangedCallback() {
                override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
                    emitter.onNext(get())
                }
            }

            addOnPropertyChangedCallback(callback)
            emitter.add { removeOnPropertyChangedCallback(callback) }
        }
    }
}

fun <T> createCompositeObservable(observable : android.databinding.Observable,
                                  valueGetter: () -> T) : ObservableField<T> {
    return createCompositeObservable(listOf(observable), valueGetter)
}

fun <T> createCompositeObservable(observables: List<android.databinding.Observable>,
                                  valueGetter: () -> T) : ObservableField<T> {
    return object : ObservableField<T>() {
        val changeListener = object : android.databinding.Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
                notifyChange()
            }
        }

        val callbackCount = AtomicInteger(0)

        override fun get(): T = valueGetter()

        override fun addOnPropertyChangedCallback(callback: android.databinding.Observable.OnPropertyChangedCallback?) {
            super.addOnPropertyChangedCallback(callback)

            if (callbackCount.getAndIncrement() == 0) {
                observables.forEach { it.addOnPropertyChangedCallback(changeListener) }
            }
        }

        override fun removeOnPropertyChangedCallback(callback: android.databinding.Observable.OnPropertyChangedCallback?) {
            super.removeOnPropertyChangedCallback(callback)

            if (callbackCount.decrementAndGet() == 0) {
                observables.forEach { it.removeOnPropertyChangedCallback(changeListener) }
            }
        }
    }
}