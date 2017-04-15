package com.xianzhitech.ptt.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.home.login.LoginFragment


class LoginActivity : BaseActivity(), LoginFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.login_app)

        if (appComponent.signalBroker.isLoggedIn) {
            navigateToHome()
        }
        else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, LoginFragment())
                    .commit()
        }
    }

    override fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}