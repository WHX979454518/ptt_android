package com.xianzhitech.ptt.ui.room

import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

/**
 * 房间对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomActivity : BaseToolbarActivity(), RoomFragment.Callbacks {

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
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.room_content) ?. let {
            if (it is BackPressable && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onRoomLoaded(name: CharSequence) {
        this.title = name
    }

    override fun onRoomQuited() {
        finish()
    }

    override fun onRoomJoined() {
        // Do nothing
    }

}
