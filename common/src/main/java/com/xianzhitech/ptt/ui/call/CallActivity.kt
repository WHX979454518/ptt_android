package com.xianzhitech.ptt.ui.call

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import com.xianzhitech.ptt.broker.RoomMode
import com.xianzhitech.ptt.ui.base.BaseActivity

class CallActivity : BaseActivity(), CallFragment.Callbacks {
    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, CallFragment().apply { arguments = intent.extras })
                    .commitNow()
        }
    }

    override fun closeCallPage() {
        finish()
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, roomMode: RoomMode) {
        (supportFragmentManager.findFragmentById(android.R.id.content) as CallFragment).joinRoom(roomId, roomMode == RoomMode.AUDIO)
    }

}