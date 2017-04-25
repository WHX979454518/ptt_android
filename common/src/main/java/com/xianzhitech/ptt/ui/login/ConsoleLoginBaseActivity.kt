package com.xianzhitech.ptt.ui.login

import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.home.login.ConsoleLoginFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment


open class ConsoleLoginBaseActivity : BaseActivity(), ConsoleLoginFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.login_app)

        if (appComponent.signalBroker.isLoggedIn) {
            navigateToHome()
        }
        else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, LoginFragment.create(
                            intent.getBooleanExtra(EXTRA_KICKED_OUT, false),
                            intent.getStringExtra(EXTRA_KICKED_OUT_REASON)))
                    .commit()
        }
    }

    override fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_KICKED_OUT = "kicked_out"
        const val EXTRA_KICKED_OUT_REASON = "kicked_out_reason"
    }
}