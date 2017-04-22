package com.xianzhitech.ptt.ui.user

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseActivity


class ChangePasswordActivity : BaseActivity(), ChangePasswordFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, ChangePasswordFragment(), TAG_FRAGMENT)
                    .commit()
        }
    }

    override fun confirmFinishing() {
        super.finish()
    }

    override fun finish() {
        if ((supportFragmentManager.findFragmentByTag(TAG_FRAGMENT) as? ChangePasswordFragment)?.requestFinish() ?: true) {
            super.finish()
        }
    }

    companion object {
        private const val TAG_FRAGMENT = "tag_frag"
    }
}