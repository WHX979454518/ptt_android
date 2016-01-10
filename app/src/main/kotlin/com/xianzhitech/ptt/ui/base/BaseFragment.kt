package com.xianzhitech.ptt.ui.base

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.View
import com.trello.rxlifecycle.FragmentEvent
import com.trello.rxlifecycle.RxLifecycle
import com.xianzhitech.ptt.presenter.base.PresenterView
import rx.Observable
import rx.subjects.BehaviorSubject

abstract class BaseFragment<T> : Fragment(), PresenterView {
    val lifecycleEventSubject = BehaviorSubject.create<FragmentEvent>()
    protected var callbacks: T? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleEventSubject.onNext(FragmentEvent.CREATE)
    }

    override fun onResume() {
        super.onResume()

        lifecycleEventSubject.onNext(FragmentEvent.RESUME)
    }

    override fun showError(err: Throwable) {
        throw RuntimeException(err)
    }

    override fun showLoading(visible: Boolean) {

    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleEventSubject.onNext(FragmentEvent.CREATE_VIEW)
    }

    override fun onStart() {
        super.onStart()

        lifecycleEventSubject.onNext(FragmentEvent.START)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        lifecycleEventSubject.onNext(FragmentEvent.ATTACH)

        if (parentFragment != null) {
            callbacks = parentFragment as T
        } else {
            callbacks = activity as T
        }
    }

    override fun onDetach() {
        lifecycleEventSubject.onNext(FragmentEvent.DETACH)

        callbacks = null
        super.onDetach()
    }

    override fun onStop() {
        lifecycleEventSubject.onNext(FragmentEvent.STOP)
        super.onStop()
    }

    override fun onDestroyView() {
        lifecycleEventSubject.onNext(FragmentEvent.DESTROY_VIEW)
        super.onDestroyView()
    }

    override fun onPause() {
        lifecycleEventSubject.onNext(FragmentEvent.PAUSE)
        super.onPause()
    }

    override fun onDestroy() {
        lifecycleEventSubject.onNext(FragmentEvent.DESTROY)
        super.onDestroy()
    }

    fun <D> bindToLifecycle(): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindFragment<D>(lifecycleEventSubject)
    }

    fun <D> bindUntil(event: FragmentEvent): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindUntilFragmentEvent<D>(lifecycleEventSubject, event)
    }
}
