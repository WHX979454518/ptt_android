package com.xianzhitech.ptt.media

import android.content.Intent
import android.view.KeyEvent
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.toast
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("MediaButtonHandler")

class MediaButtonHandler(private val signalService: SignalBroker) {
    private var lastHeadsetReleaseTime = 0L

    private fun canRequestMic() = signalService.currentWalkieRoomState.value.canRequestMic(signalService.currentUser.value.orNull())

    fun handleMediaButtonEvent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent: KeyEvent? = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null) {
                logger.i { "Got key event $keyEvent" }
                if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                    // Only handle down events
                    return
                }

                if (signalService.currentWalkieRoomState.value.status.inRoom) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> signalService.grabWalkieMic().toCompletable().logErrorAndForget(Throwable::toast).subscribe()
                        KeyEvent.KEYCODE_MEDIA_STOP -> signalService.releaseMic()
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            if (System.currentTimeMillis() - lastHeadsetReleaseTime < 800) {
                                // 刚刚释放没超过800毫秒不允许操作
                                lastHeadsetReleaseTime = 0L
                            } else if (canRequestMic()) {
                                signalService.grabWalkieMic().toCompletable().logErrorAndForget(Throwable::toast).subscribe()
                                lastHeadsetReleaseTime = 0L
                            } else {
                                signalService.releaseMic()
                                lastHeadsetReleaseTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        }
    }

}