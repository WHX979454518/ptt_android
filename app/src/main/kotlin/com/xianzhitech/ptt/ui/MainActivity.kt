package com.xianzhitech.ptt.ui

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view.View
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.sio.SocketIOBackgroundService
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment

class MainActivity : BaseActivity(), LoginFragment.Callbacks, HomeFragment.Callbacks {

    private lateinit var toolbar: Toolbar
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startService(Intent(this, SocketIOBackgroundService::class.java))

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

        (application as AppComponent).connectToBackgroundService()
                .flatMap { it.loginState }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    if (it.status == LoginState.Status.IDLE) {
                        displayFragment(LoginFragment::class.java)
                    } else if (it.status == LoginState.Status.LOGGED_IN) {
                        displayFragment(HomeFragment::class.java)
                    }

                    progress.setVisible(it.status == LoginState.Status.LOGIN_IN_PROGRESS)
                }
    }

    private fun displayFragment(fragmentClazz: Class<out Fragment>) {
        if (fragmentClazz != supportFragmentManager.findFragmentById(R.id.main_content)?.javaClass) {
            supportFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.main_content, Fragment.instantiate(this, fragmentClazz.name))
                    .commit();
        }
    }

}
