package com.xianzhitech.ptt.viewmodel

import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.data.exception.ServerException
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.e
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class LoginViewModel(private val appComponent: AppComponent,
                     private val navigator : Navigator) : LifecycleViewModel() {

    val name = ObservableField<String>("500033")
    val password = ObservableField<String>("000000")
    val isLogging = ObservableBoolean()
    val loginButtonEnabled = createCompositeObservable(listOf<Observable>(name, password, isLogging)) {
        name.get().isNullOrBlank().not() && password.get().isNullOrBlank().not() && isLogging.get().not()
    }

    val nameError = ObservableField<String>()
    val passwordError = ObservableField<String>()

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
            val err = (throwable as? ServerException)?.message ?: App.instance.getString(R.string.error_unknown)
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