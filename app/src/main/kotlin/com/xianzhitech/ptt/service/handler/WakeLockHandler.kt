package com.xianzhitech.ptt.service.handler

import android.content.Context
import android.os.PowerManager
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.ext.i
import org.slf4j.LoggerFactory


class WakeLockHandler(signalBroker: SignalBroker,
                      appContext: Context) {

    init {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ptt")
        val logger = LoggerFactory.getLogger("WakeLockHandler")

        signalBroker.currentUser
                .map { it.isPresent }
                .distinctUntilChanged()
                .subscribe { loggedIn ->
                    if (loggedIn && wakeLock.isHeld.not()) {
                        wakeLock.acquire()
                        logger.i { "Acquired wakelock..." }
                    }
                    else if (loggedIn.not() && wakeLock.isHeld) {
                        wakeLock.release()
                        logger.i { "Released wakelock..." }
                    }
                }
    }
}