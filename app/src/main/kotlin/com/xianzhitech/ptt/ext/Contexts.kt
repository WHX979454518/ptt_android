package com.xianzhitech.ptt.ext

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import com.github.pwittchen.reactivenetwork.library.rx2.ConnectivityPredicate
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseActivity
import io.reactivex.Completable
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("ContextUtils")

fun Context.receiveBroadcasts(vararg actions: String): io.reactivex.Observable<Intent> {
    return io.reactivex.Observable.create<Intent> { emitter ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                emitter.onNext(intent)
            }
        }

        val filter = IntentFilter().apply { actions.forEach(this::addAction) }
        registerReceiver(receiver, filter)

        emitter.setCancellable { unregisterReceiver(receiver) }
    }
}

fun ConnectivityManager.hasActiveConnection(): Boolean {
    return activeNetworkInfo?.isConnected ?: false
}


fun Context.hasActiveConnection() : Boolean {
    return (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).hasActiveConnection()
}

fun Context.waitForConnection() : Completable {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    return Completable.defer {
        if (connectivityManager.hasActiveConnection()) {
            return@defer Completable.complete()
        }

        receiveBroadcasts(ConnectivityManager.CONNECTIVITY_ACTION)
                .filter { hasActiveConnection() }
                .firstOrError()
                .toCompletable()
    }
}

fun Context.getConnectivityObservable() : io.reactivex.Observable<Boolean> {
    return ReactiveNetwork.observeNetworkConnectivity(this)
            .map(ConnectivityPredicate.hasState(NetworkInfo.State.CONNECTED))
            .distinctUntilChanged()
}


inline fun <reified T : Fragment> FragmentManager.findFragment(tag: String): T? {
    return findFragmentByTag(tag) as? T
}

inline fun <reified T> Fragment.callbacks(): T? {
    return (parentFragment as? T) ?: (activity as? T)
}

val Context.appComponent: AppComponent
    get() = applicationContext as AppComponent

val Fragment.appComponent: AppComponent
    get() = activity.appComponent

fun DialogFragment.dismissImmediately() {
    dismiss()
    fragmentManager.executePendingTransactions()
}

fun Activity.startActivityWithAnimation(intent: Intent,
                                        enterAnim: Int = R.anim.slide_in_from_right,
                                        exitAnim: Int = R.anim.slide_out_to_left,
                                        backEnterAnim: Int = R.anim.slide_in_from_left,
                                        backExitAnim: Int = R.anim.slide_out_to_right) {
    intent.putExtra(BaseActivity.EXTRA_FINISH_ENTER_ANIM, backEnterAnim)
    intent.putExtra(BaseActivity.EXTRA_FINISH_EXIT_ANIM, backExitAnim)
    ActivityCompat.startActivity(this, intent, ActivityOptionsCompat.makeCustomAnimation(this, enterAnim, exitAnim).toBundle())
    overridePendingTransition(enterAnim, exitAnim)
}

fun Activity.startActivityForResultWithAnimation(intent: Intent,
                                                 requestCode: Int,
                                                 enterAnim: Int = R.anim.slide_in_from_right,
                                                 exitAnim: Int = R.anim.slide_out_to_left,
                                                 backEnterAnim: Int = R.anim.slide_in_from_left,
                                                 backExitAnim: Int = R.anim.slide_out_to_right) {

    intent.putExtra(BaseActivity.EXTRA_FINISH_ENTER_ANIM, backEnterAnim)
    intent.putExtra(BaseActivity.EXTRA_FINISH_EXIT_ANIM, backExitAnim)
    ActivityCompat.startActivityForResult(this, intent, requestCode, ActivityOptionsCompat.makeCustomAnimation(this, enterAnim, exitAnim).toBundle())
    overridePendingTransition(enterAnim, exitAnim)
}