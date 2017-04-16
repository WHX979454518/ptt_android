package com.xianzhitech.ptt.ui.base

import android.support.v7.app.AppCompatDialogFragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.LoggerFactory
import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class BaseFragment : AppCompatDialogFragment() {
    private var subscriptions : CompositeSubscription? = null
    private var disposable : CompositeDisposable? = null

    val logger = LoggerFactory.getLogger(javaClass)

    override fun onStop() {
        super.onStop()

        subscriptions?.unsubscribe()
        subscriptions = null

        disposable?.dispose()
        disposable = null
    }

    protected fun Subscription.bindToLifecycle() : Subscription {
        if (subscriptions == null) {
            subscriptions = CompositeSubscription()
        }

        subscriptions!!.add(this)
        return this
    }

    fun Disposable.bindToLifecycle() : Disposable {
        if (disposable == null) {
            disposable = CompositeDisposable()
        }

        disposable!!.add(this)
        return this
    }
}
