package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.content.Intent
import android.os.Bundle

import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity

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
        supportFragmentManager.beginTransaction()
                .replace(R.id.room_content, RoomFragment.create(intent.getSerializableExtra(EXTRA_ROOM_REQUEST) as JoinRoomRequest))
                .commit()
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.room_content) ?. let {
            if (it is BackPressable) {
                it.onBackPressed()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onRoomQuited() {
        finish()
    }

    companion object {

        val EXTRA_ROOM_REQUEST = "extra_room_request"

        fun builder(context: Context, roomRequest: JoinRoomRequest): Intent {
            return Intent(context, RoomActivity::class.java).putExtra(EXTRA_ROOM_REQUEST, roomRequest)
        }
    }
}
