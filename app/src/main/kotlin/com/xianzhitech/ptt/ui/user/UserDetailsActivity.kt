package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class UserDetailsActivity : BaseToolbarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_user_details)
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun build(context: Context, userId: String) : Intent {
            return Intent(context, UserDetailsActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
        }
    }
}