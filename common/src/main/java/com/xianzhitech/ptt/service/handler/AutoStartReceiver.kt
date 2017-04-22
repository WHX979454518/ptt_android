package com.xianzhitech.ptt.service.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 作为开机启动的监听者。其实啥都不做, 只是为了把App跑起来
 */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        //no op
    }
}