package com.xianzhitech.ptt.ui.base

import android.support.v7.app.AppCompatDialogFragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class BaseFragment : AppCompatDialogFragment() {
    private var disposable : CompositeDisposable? = null

    val logger : Logger = LoggerFactory.getLogger(javaClass)

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
        disposable = null
    }

    fun Disposable.bindToLifecycle() : Disposable {
        if (disposable == null) {
            disposable = CompositeDisposable()
        }

        disposable!!.add(this)
        return this
    }
}
