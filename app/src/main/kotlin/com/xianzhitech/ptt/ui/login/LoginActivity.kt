package com.xianzhitech.ptt.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.home.login.LoginFragment


class LoginActivity : AppCompatActivity(), LoginFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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