package com.xianzhitech.ptt.ext

import android.content.Context
import android.support.v4.app.FragmentActivity
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import rx.Observable
import rx.Subscriber

/**
 * Created by fanchao on 17/12/15.
 */

fun <T> Observable<T>.toBlockingFirst() = toBlocking().first()

open class GlobalSubscriber<T>(val context: Context? = null) : Subscriber<T>() {
    companion object {
        public const val FRAG_ERROR_DIALOG = "frag_error_dialog"
    }

    override fun onError(e: Throwable?) {
        if (context is FragmentActivity && e != null) {
            AlertDialogFragment.Builder()
                    .setTitle(context.getString(R.string.error_title))
                    .setMessage(context.getString(R.string.error_content, e.message))
                    .show(context.supportFragmentManager, FRAG_ERROR_DIALOG)
        }
    }

    override fun onNext(t: T) {
    }

    override fun onCompleted() {
    }
}