package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.LoginResult
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import rx.Subscription
import java.io.Serializable
import kotlin.collections.forEach

/**
 *
 * 负责登陆相关的逻辑
 *
 * Created by fanchao on 9/01/16.
 */
class LoginPresenter(private val authProvider: AuthProvider,
                     private val userRepository: UserRepository,
                     private val preferenceProvider: PreferenceStorageProvider) : BasePresenter<LoginPresenterView>() {

    companion object {
        const val PREF_KEY_TOKEN = "login_token"
    }

    private var loginSubscription: Subscription? = null
    private val loginSubscriber = object : GlobalSubscriber<LoginResult>() {
        override fun onError(e: Throwable) {
            views.forEach {
                it.showLogin()
                it.showLoading(false)
                it.showError(e)
            }
            preferenceProvider.remove(PREF_KEY_TOKEN)
            loginSubscription = null
        }

        override fun onNext(t: LoginResult) {
            preferenceProvider.save(PREF_KEY_TOKEN, t.token)
            views.forEach {
                it.showLoading(false)
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

    override fun attachView(view: LoginPresenterView) {
        super.attachView(view)

        if (authProvider.peekCurrentLogonUser() != null) {
            view.showLoginSuccess()
        } else if (loginSubscription != null) {
            view.showLoading(true)
        } else {
            view.showLogin()
            view.showLoading(false)
        }
    }

    fun requestLogin(name: String, password: String) {
        views.forEach { it.showLoading(true) }

        if (loginSubscription != null) {
            loginSubscription?.unsubscribe()
        }

        preferenceProvider.remove(PREF_KEY_TOKEN)
        loginSubscription = authProvider.login(name, password).observeOnMainThread().subscribe(loginSubscriber)
    }

    fun cancelLogin() = loginSubscription?.let {
        it.unsubscribe()
        views.forEach { it.showLoading(false) }
        loginSubscription = null
        true
    } ?: false

}

