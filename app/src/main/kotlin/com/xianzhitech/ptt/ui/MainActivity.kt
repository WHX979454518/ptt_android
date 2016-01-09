package com.xianzhitech.ptt.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view.View
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.presenter.LoginView

class MainActivity : BaseActivity(), LoginFragment.Callbacks, HomeFragment.Callbacks, LoginView {

    private lateinit var toolbar: Toolbar
    private lateinit var loginPresenter: LoginPresenter
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        toolbar = findView(R.id.main_toolbar)
        progress = findView(R.id.main_progress)
        setSupportActionBar(toolbar)
    }

    override fun onBackPressed() {
        (supportFragmentManager.findFragmentById(R.id.main_content) as? BackPressable)?.onBackPressed() ?: super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()

        loginPresenter = (application as AppComponent).loginPresenter
        loginPresenter.attachView(this)
    }

    override fun onStop() {
        loginPresenter.detachView(this)

        super.onStop()
    }

    private fun displayFragment(fragmentClazz: Class<out Fragment>) {
        if (fragmentClazz != supportFragmentManager.findFragmentById(R.id.main_content)?.javaClass) {
            supportFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.main_content, Fragment.instantiate(this, fragmentClazz.name))
                    .commit();
        }
    }

    override fun showLogin() {
        displayFragment(LoginFragment::class.java)
    }

    override fun showLoginSuccess() {
        displayFragment(HomeFragment::class.java)
    }

    override fun showLoginInProgress(inProgress: Boolean) {
        progress.setVisible(inProgress)
    }

    override fun showLoginError(message: CharSequence?) {
        // Do nothing.
    }
}
