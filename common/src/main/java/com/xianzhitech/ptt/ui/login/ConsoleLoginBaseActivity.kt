package com.xianzhitech.ptt.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import com.xianzhitech.ptt.ui.home.login.ConsoleLoginFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment


open class ConsoleLoginBaseActivity : AppCompatActivity(), ConsoleLoginFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.login_app)
       /* supportActionBar!!.hide()// 隐藏ActionBar
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)//remove notification bar 即全屏*/

        if (appComponent.signalBroker.isLoggedIn) {
            navigateToHome()
        }
        else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, ConsoleLoginFragment.create(
                            intent.getBooleanExtra(EXTRA_KICKED_OUT, false),
                            intent.getStringExtra(EXTRA_KICKED_OUT_REASON)))
                    .commit()
        }
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