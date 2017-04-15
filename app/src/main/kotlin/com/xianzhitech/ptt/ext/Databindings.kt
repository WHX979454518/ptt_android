package com.xianzhitech.ptt.ext

import android.databinding.ObservableField
import com.google.common.base.Optional
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.atomic.AtomicInteger

private open class CompositeObservable<T>(private val observables: List<android.databinding.Observable>,
                                          private val valueGetter: () -> T?) : ObservableField<T>() {
    val changeListener = object : android.databinding.Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
            notifyChange()
        }
    }

    val callbackCount = AtomicInteger(0)

    override fun get(): T? = valueGetter()

    override fun addOnPropertyChangedCallback(callback: android.databinding.Observable.OnPropertyChangedCallback?) {
        super.addOnPropertyChangedCallback(callback)

        if (callbackCount.getAndIncrement() == 0) {
            onFirstCallbackAdded()
        }
    }

    open protected fun onFirstCallbackAdded() {
        observables.forEach { it.addOnPropertyChangedCallback(changeListener) }
    }

    override fun removeOnPropertyChangedCallback(callback: android.databinding.Observable.OnPropertyChangedCallback?) {
        super.removeOnPropertyChangedCallback(callback)

        if (callbackCount.decrementAndGet() == 0) {
            onLastCallbackRemoved()
        }
    }

    open protected fun onLastCallbackRemoved() {
        observables.forEach { it.removeOnPropertyChangedCallback(changeListener) }
    }
}

fun <T> ObservableField<T>.toRxObservable(): io.reactivex.Observable<T> {
    return io.reactivex.Observable.create { emitter ->
        emitter.onNext(get())

        val callback = object : android.databinding.Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
                emitter.onNext(get())
            }
        }

        addOnPropertyChangedCallback(callback)
        emitter.setCancellable { removeOnPropertyChangedCallback(callback) }
    }
}

fun <T> createCompositeObservable(observable: android.databinding.Observable,
                                  valueGetter: () -> T): ObservableField<T> {
    return createCompositeObservable(listOf(observable), valueGetter)
}

fun <T> createCompositeObservable(observables: List<android.databinding.Observable>,
                                  valueGetter: () -> T): ObservableField<T> {
    return CompositeObservable<T>(observables, valueGetter)
}

fun <T> BehaviorSubject<Optional<T>>.toObservableField(): ObservableField<T> {
    return object : CompositeObservable<T>(emptyList(), { value.orNull() }) {
        var disposable: Disposable? = null

        override fun onFirstCallbackAdded() {
            super.onFirstCallbackAdded()

            disposable = subscribe { notifyChange() }
        }

        override fun onLastCallbackRemoved() {
            super.onLastCallbackRemoved()

            disposable?.dispose()
            disposable = null
        }
    }
}