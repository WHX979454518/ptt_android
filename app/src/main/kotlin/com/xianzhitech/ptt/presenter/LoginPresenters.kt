package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.presenter.base.PresenterView
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.LoginResult
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import rx.Subscription
import java.io.Serializable

/**
 *
 * 负责登陆相关的逻辑
 *
 * Created by fanchao on 9/01/16.
 */
class LoginPresenter(private val authProvider: AuthProvider,
                     private val preferenceProvider: PreferenceStorageProvider) : BasePresenter<LoginView>() {

    companion object {
        const val PREF_KEY_TOKEN = "login_token"
    }

    private var loginSubscription: Subscription? = null
    private val loginSubscriber = object : GlobalSubscriber<LoginResult>() {
        override fun onError(e: Throwable) {
            views.forEach {
                it.showLogin()
                it.showLoginInProgress(false)
                it.showLoginError(e.message)
            }
            preferenceProvider.remove(PREF_KEY_TOKEN)
            loginSubscription = null
        }

        override fun onNext(t: LoginResult) {
            preferenceProvider.save(PREF_KEY_TOKEN, t.token)
            views.forEach {
                it.showLoginInProgress(false)
                it.showLoginSuccess()
            }
            loginSubscription = null
        }
    }

    init {
        (preferenceProvider.get(PREF_KEY_TOKEN) as? Serializable)?.let {
            loginSubscription = authProvider.resumeLogin(it).observeOnMainThread().subscribe(loginSubscriber)
        }
    }

    override fun attachView(view: LoginView) {
        super.attachView(view)

        if (authProvider.peekCurrentLogonUserId() != null) {
            view.showLoginSuccess()
        } else if (loginSubscription != null) {
            view.showLoginInProgress(true)
        } else {
            view.showLogin()
            view.showLoginInProgress(false)
        }
    }

    fun requestLogin(name: String, password: String) {
        views.forEach { it.showLoginInProgress(true) }

        if (loginSubscription != null) {
            loginSubscription?.unsubscribe()
        }

        preferenceProvider.remove(PREF_KEY_TOKEN)

        loginSubscription = authProvider.login(name, password).observeOnMainThread().subscribe(loginSubscriber)
    }

    fun cancelLogin() = loginSubscription?.let {
        it.unsubscribe()
        views.forEach { it.showLoginInProgress(false) }
        loginSubscription = null
        true
    } ?: false

}

interface LoginView : PresenterView {
    fun showLogin()
    fun showLoginSuccess()
    fun showLoginError(message: CharSequence?)
    fun showLoginInProgress(inProgress: Boolean)
}