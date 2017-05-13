package com.xianzhitech.ptt.ui.walkie

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import com.xianzhitech.ptt.broker.RoomMode
import com.xianzhitech.ptt.ui.base.BaseActivity


class WalkieRoomActivity : BaseActivity(), WalkieRoomFragment.Callbacks {
    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, WalkieRoomFragment().apply { arguments = intent.extras })
                    .commit()
        }
    }

    override fun closeRoomPage() {
        finish()
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, roomMode: RoomMode) {
        (supportFragmentManager.findFragmentById(android.R.id.content) as? WalkieRoomFragment)?.joinRoom(roomId, fromInvitation)
    }
}