package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class UserDetailsActivity : BaseToolbarActivity(), UserDetailsFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.baseToolbar_root, UserDetailsFragment.createInstance(intent.extras.getString(EXTRA_USER_ID)))
                    .commit()
        }
    }

    override fun navigateToWalkieTalkiePage(roomId: String) {
        joinRoom(roomId, false, false)
    }

    override fun navigateToVideoChatPage(roomId: String) {
        joinRoom(roomId, false, true)
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun build(context: Context, userId: String): Intent {
            return Intent(context, UserDetailsActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
        }
    }
}