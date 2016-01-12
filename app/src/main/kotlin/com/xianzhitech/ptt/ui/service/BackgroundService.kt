package com.xianzhitech.ptt.ui.service

import android.app.Service
import android.content.Intent
import com.xianzhitech.ptt.service.provider.ConversationRequest
import com.xianzhitech.ptt.ui.room.RoomActivity

class BackgroundService : Service() {
    companion object {
        public const val ACTION_OPEN_ROOM = "open_room"
        public const val EXTRA_CONVERSATION_REQUEST = "extra_conv_request"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN_ROOM -> {
                startActivity(RoomActivity.builder(this, intent!!.getSerializableExtra(EXTRA_CONVERSATION_REQUEST) as ConversationRequest)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null
}