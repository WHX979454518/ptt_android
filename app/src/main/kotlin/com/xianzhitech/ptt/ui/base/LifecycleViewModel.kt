package com.xianzhitech.ptt.ui.base

import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class LifecycleViewModel {
    private var subscription : CompositeSubscription? = null

    open fun onStart() {
    }

    open fun onStop() {
        subscription?.unsubscribe()
        subscription = null
    }

    fun Subscription.bindToLifecycle() : Subscription {
        if (subscription == null) {
            subscription = CompositeSubscription()
        }

        subscription!!.add(this)
        return this
    }
}