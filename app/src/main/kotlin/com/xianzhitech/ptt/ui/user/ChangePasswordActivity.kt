package com.xianzhitech.ptt.ui.user

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ChangePasswordActivity : BaseToolbarActivity(), ChangePasswordFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_change_password)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.content, ChangePasswordFragment(), TAG_FRAGMENT)
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