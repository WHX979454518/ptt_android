package com.xianzhitech.ptt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.service.PushService

/**
 * 在MIUI机型上这个跨进程的广播可能会失效。所以用这个Activity来转发一下
 */
class RoomInvitationHelperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sendBroadcast(Intent(PushService.ACTION_MESSAGE).putExtra(PushService.EXTRA_MSG, intent.getStringExtra(EXTRA_MSG)))
        finish()
    }

    companion object {
        const val EXTRA_MSG = "msg"
    }
}