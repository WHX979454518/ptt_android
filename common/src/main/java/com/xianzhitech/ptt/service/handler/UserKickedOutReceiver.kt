package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.event.UserKickedOutEvent
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.login.LoginActivity


class UserKickedOutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {

        if (context.appComponent.activityProvider.currentStartedActivity !is LoginActivity) {
            val event = intent?.getSerializableExtra(SignalBroker.EXTRA_EVENT)
            val reason : String? = when (event) {
                is UserKickedOutEvent -> context.getString(R.string.error_forced_logout)
                is LoginFailedEvent -> event.message ?: context.getString(R.string.error_invalid_password)
                else -> null
            }

            context.startActivity(Intent(context, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_KICKED_OUT, true)
                    .putExtra(LoginActivity.EXTRA_KICKED_OUT_REASON, reason)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}