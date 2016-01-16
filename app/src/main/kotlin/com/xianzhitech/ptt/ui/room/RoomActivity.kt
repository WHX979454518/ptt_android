package com.xianzhitech.ptt.ui.room

import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import kotlin.text.isNullOrBlank

/**
 * 房间对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomActivity : BaseActivity(), RoomFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        supportFragmentManager.apply {
            if (findFragmentById(R.id.room_content) == null) {
                beginTransaction()
                        .replace(R.id.room_content, RoomFragment())
                        .commit()
                executePendingTransactions()
            }
        }

        if (intent.getBooleanExtra(EXTRA_JOIN_ROOM_FROM_INVITE, false)) {
            intent.getStringExtra(EXTRA_JOIN_ROOM_ID).let {
                if (it.isNullOrBlank().not()) {
                    joinRoom(it, null, true, bindToLifecycle())
                }
            }
        }
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.room_content) ?. let {
            if (it is BackPressable && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onRoomQuited() {
        finish()
    }

    companion object {
        public const val EXTRA_JOIN_ROOM_FROM_INVITE = "extra_room_from_invite"
        public const val EXTRA_JOIN_ROOM_ID = "extra_room_id"
    }
}
