package com.xianzhitech.ptt.ext

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.util.Log
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.db.ResultSet
import com.xianzhitech.ptt.service.UserDescribableException
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func1
import java.util.*

/**
 * Created by fanchao on 17/12/15.
 */

fun <T> Observable<T>.toBlockingFirst() = toBlocking().first()

open class GlobalSubscriber<T>(private val context: Context? = null) : Subscriber<T>() {
    companion object {
        public const val FRAG_ERROR_DIALOG = "frag_error_dialog"
    }

    override fun onError(e: Throwable) {
        val message : CharSequence?
        if (context != null && e is UserDescribableException) {
            message = e.describe(context)
        }
        else {
            message = e.message
        }

        if (context is FragmentActivity) {
            AlertDialogFragment.Builder()
                    .setTitle(context.getString(R.string.error_title))
                    .setMessage(context.getString(R.string.error_content, message))
                    .show(context.supportFragmentManager, FRAG_ERROR_DIALOG)
        }

        Log.e("GlobalSubscriber", "Error: ${message}", e)
    }

    override fun onNext(t: T) {
    }

    override fun onCompleted() {
    }
}

fun <T> Observable<ResultSet>.mapToOne(mapper: Func1<ResultSet, T>) = map {
    it.use {
        it.moveToFirst()
        mapper.call(it)
    }
}

fun <T> Observable<ResultSet>.mapToOneOrDefault(mapper: Func1<ResultSet, T>, defaultValue: T?) = map {
    it.use {
        if (it.moveToFirst()) mapper.call(it)
        else defaultValue
    }
}

fun <T> Observable<ResultSet>.mapToList(mapper: Func1<ResultSet, T>): Observable<List<T>> = map {
    it.use {
        ArrayList<T>(it.getCount()).apply {
            while (it.moveToNext()) {
                add(mapper.call(it))
            }
        }
    }
}

fun <T> Observable<T>.subscribeOnOptional(scheduler: Scheduler?): Observable<T> = scheduler?.let { subscribeOn(it) } ?: this

fun <T> Observable<T>.observeOnMainThread() = observeOn(AndroidSchedulers.mainThread())
fun <T> Observable<T>.subscribeOnMainThread() = subscribeOn(AndroidSchedulers.mainThread())

fun <T> T?.toObservable(): Observable<T> = Observable.just(this)
