package com.xianzhitech.ptt.ext

import android.databinding.Observable
import android.databinding.ObservableField
import android.databinding.ObservableList
import android.databinding.ObservableMap
import com.google.common.base.Optional
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.atomic.AtomicInteger

private open class CompositeObservable<T>(private val observables: List<Any>,
                                          private val valueGetter: () -> T?) : ObservableField<T>() {
    val observableChangeListener = object : android.databinding.Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
            notifyChange()
        }
    }

    val mapChangeListener : ObservableMap.OnMapChangedCallback<ObservableMap<Any, Any>, Any, Any> = object : ObservableMap.OnMapChangedCallback<ObservableMap<Any, Any>, Any, Any>() {
        override fun onMapChanged(p0: ObservableMap<Any, Any>?, p1: Any?) {
            notifyChange()
        }
    }

    val listChangeListener : ObservableList.OnListChangedCallback<*> = object : ObservableList.OnListChangedCallback<ObservableList<Any>>() {
        override fun onChanged(p0: ObservableList<Any>?) {
            this@CompositeObservable.notifyChange()
        }

        override fun onItemRangeMoved(p0: ObservableList<Any>?, p1: Int, p2: Int, p3: Int) {
            onChanged(p0)
        }

        override fun onItemRangeRemoved(p0: ObservableList<Any>?, p1: Int, p2: Int) {
            onChanged(p0)
        }

        override fun onItemRangeInserted(p0: ObservableList<Any>?, p1: Int, p2: Int) {
            onChanged(p0)
        }

        override fun onItemRangeChanged(p0: ObservableList<Any>?, p1: Int, p2: Int) {
            onChanged(p0)
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

    @Suppress("UNCHECKED_CAST")
    open protected fun onFirstCallbackAdded() {
        observables.forEach {
            when (it) {
                is ObservableMap<*, *> -> (it as ObservableMap<Any, Any>).addOnMapChangedCallback(mapChangeListener)
                is Observable -> it.addOnPropertyChangedCallback(observableChangeListener)
                is ObservableList<*> -> (it as ObservableList<Any>).addOnListChangedCallback(listChangeListener)
            }
        }
    }

    override fun removeOnPropertyChangedCallback(callback: android.databinding.Observable.OnPropertyChangedCallback?) {
        super.removeOnPropertyChangedCallback(callback)

        if (callbackCount.decrementAndGet() == 0) {
            onLastCallbackRemoved()
        }
    }

    @Suppress("UNCHECKED_CAST")
    open protected fun onLastCallbackRemoved() {
        observables.forEach {
            when (it) {
                is ObservableMap<*, *> -> (it as ObservableMap<Any, Any>).removeOnMapChangedCallback(mapChangeListener)
                is Observable -> it.removeOnPropertyChangedCallback(observableChangeListener)
                is ObservableList<*> -> (it as ObservableList<Any>).removeOnListChangedCallback(listChangeListener)
            }
        }
    }
}

fun <T> ObservableField<T>.toRxObservable(): io.reactivex.Observable<Optional<T>> {
    return io.reactivex.Observable.create { emitter ->
        emitter.onNext(Optional.fromNullable(get()))

        val callback = object : android.databinding.Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: android.databinding.Observable?, p1: Int) {
                emitter.onNext(Optional.fromNullable(get()))
            }
        }

        addOnPropertyChangedCallback(callback)
        emitter.setCancellable { removeOnPropertyChangedCallback(callback) }
    }
}

fun <T> createCompositeObservable(observable: Any,
                                  valueGetter: () -> T): ObservableField<T> {
    return createCompositeObservable(listOf(observable), valueGetter)
}

fun <T> createCompositeObservable(observables: List<Any>,
                                  valueGetter: () -> T): ObservableField<T> {
    return CompositeObservable(observables, valueGetter)
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

fun <T> ObservableField<T>.fromOptional(opt : Optional<T>) {
    set(opt.orNull())
}