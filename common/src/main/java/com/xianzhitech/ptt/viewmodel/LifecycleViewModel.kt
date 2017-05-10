package com.xianzhitech.ptt.viewmodel

import android.os.Bundle
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class LifecycleViewModel : ViewModel {
    val logger : Logger = LoggerFactory.getLogger(javaClass)

    private var disposable: CompositeDisposable? = null

    private val childModels = arrayListOf<LifecycleViewModel>()

    open fun onStart() {
        childModels.forEach(LifecycleViewModel::onStart)
    }

    open fun onStop() {
        childModels.forEach(LifecycleViewModel::onStop)

        disposable?.dispose()
        disposable = null
    }

    open fun onSaveState(out : Bundle) {

    }

    open fun onRestoreState(state : Bundle) {

    }

    fun <T : LifecycleViewModel> addChildModel(model: T): T {
        return model.apply {
            this@LifecycleViewModel.childModels.add(this)
        }
    }

    fun Disposable.bindToLifecycle() : Disposable {
        if (disposable == null) {
            disposable = CompositeDisposable()
        }

        disposable!!.add(this)
        return this
    }

    fun bindToLifecycle(disposable: Disposable) {
        if (this.disposable == null) {
            this.disposable = CompositeDisposable()
        }

        this.disposable!!.add(disposable)
    }
}