package com.xianzhitech.ptt.ui.chat

import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseActivity


class ChatActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, ChatFragment.createInstance(intent.getStringExtra(EXTRA_ROOM_ID)))
                    .commit()
        }
    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"

        fun createIntent(roomId : String) : Intent {
            return Intent(App.instance, ChatActivity::class.java)
                    .putExtra(EXTRA_ROOM_ID, roomId)
        }
    }
}