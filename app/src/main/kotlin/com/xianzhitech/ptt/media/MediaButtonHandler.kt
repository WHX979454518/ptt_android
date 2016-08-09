package com.xianzhitech.ptt.media

import android.content.Intent
import android.view.KeyEvent
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.handler.SignalServiceHandler
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("MediaButtonHandler")

class MediaButtonHandler(private val signalService: SignalServiceHandler) {
    private var lastHeadsetReleaseTime = 0L

    private fun canRequestMic() = signalService.peekRoomState().canRequestMic(signalService.peekLoginState().currentUser)

    fun handleMediaButtonEvent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent: KeyEvent? = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null) {
                logger.i { "Got key event $keyEvent" }
                if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                    // Only handle down events
                    return
                }

                if (signalService.peekRoomState().status.inRoom) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> signalService.requestMic().subscribeSimple()
                        KeyEvent.KEYCODE_MEDIA_STOP -> signalService.releaseMic()
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            if (System.currentTimeMillis() - lastHeadsetReleaseTime < 800) {
                                // 刚刚释放没超过800毫秒不允许操作
                                lastHeadsetReleaseTime = 0L
                            } else if (canRequestMic()) {
                                signalService.requestMic().subscribeSimple()
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