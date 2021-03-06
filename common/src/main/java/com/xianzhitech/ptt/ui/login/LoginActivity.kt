package com.xianzhitech.ptt.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.home.login.LoginFragment


open class LoginActivity : BaseActivity(), LoginFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.login_app)

        if (appComponent.signalBroker.isLoggedIn) {
            navigateToHome()
        }
        else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, onCreateFragment())
                    .commit()
        }
    }

    open fun onCreateFragment() : Fragment {
        return LoginFragment.create(
                intent.getBooleanExtra(EXTRA_KICKED_OUT, false),
                intent.getStringExtra(EXTRA_KICKED_OUT_REASON)
        )
    }

    override fun handleJoinRoomIntent(intent: Intent) {
    }

    override fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            // Pass 
            intent.extras?.let(this::putExtras)
        })
        finish()
    }

    companion object {
        const val EXTRA_KICKED_OUT = "kicked_out"
        const val EXTRA_KICKED_OUT_REASON = "kicked_out_reason"
    }
}