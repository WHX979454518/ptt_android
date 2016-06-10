package com.xianzhitech.ptt.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.logtagd
import com.xianzhitech.ptt.ext.subscribeSimple


class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val signalService = (context.applicationContext as AppComponent).signalHandler

            val keyEvent: KeyEvent? = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null) {
                logtagd("MEDIAKEY", "Got key event %s", keyEvent)
                if (signalService.peekRoomState().status.inRoom) {
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY &&
                            keyEvent.action == KeyEvent.ACTION_DOWN) {
                        signalService.requestMic().subscribeSimple()
                    } else if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_STOP &&
                            keyEvent.action == KeyEvent.ACTION_UP) {
                        signalService.releaseMic()
                    }
                }
            }
        }
    }

    companion object {
        fun registerMediaButtonEvent(context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerMediaButtonEventReceiver(ComponentName(context, MediaButtonReceiver::class.java))
        }

        fun unregisterMediaButtonEvent(context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.unregisterMediaButtonEventReceiver(ComponentName(context, MediaButtonReceiver::class.java))
        }
    }
}