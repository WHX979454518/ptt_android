package com.xianzhitech.ptt.viewmodel

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class LifecycleViewModel : ViewModel {
    val logger : Logger = LoggerFactory.getLogger(javaClass)

    private var disposable: CompositeDisposable? = null
    private var subscription : CompositeSubscription? = null

    private val childModels = arrayListOf<LifecycleViewModel>()

    open fun onStart() {
        childModels.forEach(LifecycleViewModel::onStart)
    }

    open fun onStop() {
        childModels.forEach(LifecycleViewModel::onStop)

        disposable?.dispose()
        disposable = null

        subscription?.unsubscribe()
        subscription = null
    }

    fun <T : LifecycleViewModel> addChildModel(model: T): T {
        return model.apply {
            this@LifecycleViewModel.childModels.add(this)
        }
    }

    fun Subscription.bindToLifecycle(): Subscription {
        if (subscription == null) {
            subscription = CompositeSubscription()
        }

        subscription!!.add(this)
        return this
    }

    fun bindToLifecycle(disposable: Disposable) {
        if (this.disposable == null) {
            this.disposable = CompositeDisposable()
        }

        this.disposable!!.add(disposable)
    }
}