package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.ui.login.LoginActivity


class UserKickedOutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.startActivity(Intent(context, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_KICKED_OUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}