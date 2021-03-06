package com.xianzhitech.ptt.viewmodel

import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.service.ServerException
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class LoginViewModel(private val appComponent: AppComponent,
                     private val navigator : Navigator,
                     kickedOut : Boolean,
                     kickedOutReason : String?) : LifecycleViewModel() {

    val name = ObservableField<String>((500000 + (Math.random() * 100).toInt()).toString())
    val password = ObservableField<String>("000000")
    val isLogging = ObservableBoolean()
    val loginButtonEnabled = createCompositeObservable(listOf<Observable>(name, password, isLogging)) {
        name.get().isNullOrBlank().not() && password.get().isNullOrBlank().not() && isLogging.get().not()
    }

    val nameError = ObservableField<String>(if (kickedOut) kickedOutReason ?: BaseApp.instance.getString(R.string.error_forced_logout) else null)
    val passwordError = ObservableField<String>(if (kickedOut) kickedOutReason ?: BaseApp.instance.getString(R.string.error_forced_logout) else null)

    override fun onStop() {
        super.onStop()

        if (appComponent.signalBroker.connectionState.value == SignalApi.ConnectionState.CONNECTING) {
            appComponent.signalBroker.logout()
        }
    }

    fun onClickLogin() {
        nameError.set(null)
        passwordError.set(null)

        appComponent.signalBroker
                .login(name.get(), password.get())
                .timeout(15, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .doOnLoading(isLogging::set)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(this::bindToLifecycle)
                .subscribe(navigator::navigateToHome, this::onLoginError)
    }

    private fun onLoginError(throwable: Throwable) {
        logger.e(throwable) { "Login error" }

        if (throwable is TimeoutException) {
            appComponent.signalBroker.logout()
            navigator.displayTimeoutError()
        }
        else {
            val err = (throwable as? ServerException)?.message ?: BaseApp.instance.getString(R.string.error_unknown)
            nameError.set(err)
            passwordError.set(err)
        }
    }

    fun onClickShare() {
        navigator.navigateToShare()
    }

    fun onClickAbout() {
        navigator.navigateToAbout()
    }

    interface Navigator {
        fun navigateToHome()
        fun navigateToShare()
        fun navigateToAbout()
        fun displayTimeoutError()
    }
}