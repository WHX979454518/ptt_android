package com.xianzhitech.ptt.ui.base

import android.support.v7.app.AppCompatDialogFragment
import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class BaseFragment : AppCompatDialogFragment() {
    private var subscriptions : CompositeSubscription? = null

    override fun onStop() {
        super.onStop()

        subscriptions?.unsubscribe()
        subscriptions = null
    }

    protected fun Subscription.bindToLifecycle() : Subscription {
        if (subscriptions == null) {
            subscriptions = CompositeSubscription()
        }

        subscriptions!!.add(this)
        return this
    }
}
