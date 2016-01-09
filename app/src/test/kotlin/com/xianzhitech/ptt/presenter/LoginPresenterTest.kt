package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.MockPreferenceProvider
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.MockAuthProvider
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

/**
 *
 * Created by fanchao on 9/01/16.
 */

@RunWith(MockitoJUnitRunner::class)
class LoginPresenterTest {

    private lateinit var presenter: LoginPresenter
    private lateinit var authProvider: AuthProvider
    private lateinit var preferenceProvider: PreferenceStorageProvider

    @Before
    fun init() {
        authProvider = MockAuthProvider()
        preferenceProvider = MockPreferenceProvider()
        presenter = LoginPresenter(authProvider, preferenceProvider)
    }

    @Test
    fun testLogin() {
        val view = MockLoginView()
        presenter.attachView(view)
        Assert.assertEquals(true, view.showedLogin)
        Assert.assertEquals(false, view.showedLoginSuccess)
        Assert.assertEquals(false, view.showedLoginError)
        Assert.assertEquals(false, view.showedLoginInProgress)
    }

    @Test
    fun testLoginSuccess() {
        val view = MockLoginView()
        val testFunc = {
            Assert.assertEquals(true, view.showedLoginSuccess)
            Assert.assertEquals(false, view.showedLoginError)
        }

        presenter.attachView(view)
        presenter.requestLogin(MockAuthProvider.LEGIT_USER.name, "")
        testFunc()

        presenter.detachView(view)
        presenter.attachView(view)
        testFunc()
    }

    @Test
    fun testLoginFailed() {
        val view = MockLoginView()
        val testFunc = {
            Assert.assertEquals(false, view.showedLoginSuccess)
            Assert.assertEquals(true, view.showedLoginError)
        }

        presenter.attachView(view)
        presenter.requestLogin(MockAuthProvider.LEGIT_USER.name + ".not", "")
        testFunc()

        presenter.detachView(view)
        presenter.attachView(view)
        testFunc()
    }

    @Test
    fun testResumeLogin() {
        val view = MockLoginView()
        val testFunc = {
            Assert.assertEquals(true, view.showedLoginSuccess)
            Assert.assertEquals(false, view.showedLoginError)
        }

        presenter.attachView(view)
        presenter.requestLogin(MockAuthProvider.LEGIT_USER.name, "")
        testFunc()

        presenter.detachView(view)

        val newPresenter = LoginPresenter(authProvider, preferenceProvider)
        newPresenter.attachView(view)
        testFunc()
    }

    private class MockLoginView : LoginView {
        var showedLogin = false
        var showedLoginSuccess = false
        var showedLoginError = false
        var showedLoginInProgress = false

        override fun showLogin() {
            showedLogin = true
        }

        override fun showLoginSuccess() {
            showedLoginSuccess = true
        }

        override fun showLoginError(message: CharSequence?) {
            showedLoginError = true
        }

        override fun showLoginInProgress(inProgress: Boolean) {
            showedLoginInProgress = inProgress
        }
    }
}