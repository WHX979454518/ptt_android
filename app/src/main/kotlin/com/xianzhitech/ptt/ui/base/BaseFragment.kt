package com.xianzhitech.ptt.ui.base

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.View
import com.trello.rxlifecycle.FragmentEvent
import com.trello.rxlifecycle.RxLifecycle
import rx.Observable
import rx.subjects.BehaviorSubject

abstract class BaseFragment : Fragment() {
    val lifecycleEventSubject = BehaviorSubject.create<FragmentEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleEventSubject.onNext(FragmentEvent.CREATE)
    }

    override fun onResume() {
        super.onResume()

        lifecycleEventSubject.onNext(FragmentEvent.RESUME)
    }

    //    override fun showError(err: Throwable) {
    //        if (err is UserDescribableException) {
    //            AlertDialogFragment.Builder()
    //                    .setTitle(R.string.error_title.toFormattedString(context))
    //                    .setMessage(err.describe(context))
    //                    .setBtnNeutral(R.string.dialog_ok.toFormattedString(context))
    //                    .show(childFragmentManager, TAG_GENERIC_ERROR_DIALOG)
    //        } else {
    //            AlertDialogFragment.Builder()
    //                    .setTitle(R.string.error_title.toFormattedString(context))
    //                    .setMessage("Class: ${err.javaClass}, msg: ${err.message}")
    //                    .setBtnNeutral(R.string.dialog_ok.toFormattedString(context))
    //                    .show(childFragmentManager, TAG_GENERIC_ERROR_DIALOG)
    //        }
    //    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
    }

    override fun onDetach() {
        lifecycleEventSubject.onNext(FragmentEvent.DETACH)

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

    companion object {
        public const val TAG_GENERIC_ERROR_DIALOG = "tag_generic_error_dialog"
    }
}
