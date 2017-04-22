package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Message


class NewMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msg = intent.getSerializableExtra(SignalBroker.EXTRA_EVENT) as Message

        when (msg.body) {

        }
    }
}