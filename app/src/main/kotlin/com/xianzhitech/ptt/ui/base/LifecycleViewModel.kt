package com.xianzhitech.ptt.ui.base

import com.xianzhitech.ptt.ui.util.ViewModel
import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class LifecycleViewModel : ViewModel {
    private var subscription : CompositeSubscription? = null
    private val childModels = arrayListOf<LifecycleViewModel>()

    open fun onStart() {
        childModels.forEach(LifecycleViewModel::onStart)
    }

    open fun onStop() {
        childModels.forEach(LifecycleViewModel::onStop)

        subscription?.unsubscribe()
        subscription = null
    }

    fun <T : LifecycleViewModel> addChildModel(model : T) : T {
        return model.apply {
            this@LifecycleViewModel.childModels.add(this)
        }
    }

    fun Subscription.bindToLifecycle() : Subscription {
        if (subscription == null) {
            subscription = CompositeSubscription()
        }

        subscription!!.add(this)
        return this
    }
}