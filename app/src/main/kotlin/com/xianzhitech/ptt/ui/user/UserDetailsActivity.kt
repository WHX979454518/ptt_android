package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseActivity

class UserDetailsActivity : BaseActivity(), UserDetailsFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, UserDetailsFragment.createInstance(intent.extras.getString(EXTRA_USER_ID)))
                    .commit()
        }
    }


    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun build(context: Context, userId: String): Intent {
            return Intent(context, UserDetailsActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
        }
    }
}