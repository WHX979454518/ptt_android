package com.xianzhitech.ptt.ui.call

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.ActivityCallBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.service.handler.GroupChatView
import com.xianzhitech.ptt.ui.base.BaseActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import org.webrtc.EglBase
import org.webrtc.VideoRenderer

class CallActivity : BaseActivity(), GroupChatView {
    private lateinit var binding : ActivityCallBinding
    private val audioManager : AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callRemoteView.init(eglBase.eglBaseContext, null)
        binding.callEnd.setOnClickListener {
            appComponent.signalBroker.quitVideoRoom()
            finish()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding.speakerOn = audioManager.isSpeakerphoneOn

        binding.callSpeaker.setOnClickListener {
            val newSpeakerOn = audioManager.isSpeakerphoneOn.not()
            audioManager.isSpeakerphoneOn = newSpeakerOn
            if (newSpeakerOn) {
                Toast.makeText(this, R.string.speaker_is_on, Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(this, R.string.speaker_is_off, Toast.LENGTH_SHORT).show()
            }
            binding.speakerOn = newSpeakerOn
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.callRemoteView.release()
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, isVideoChat: Boolean) {
        if (appComponent.signalBroker.currentVideoRoomId.value.orNull() != roomId) {
            appComponent.signalBroker.joinVideoRoom(roomId, false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .logErrorAndForget {
                        Toast.makeText(this, it.describeInHumanMessage(this), Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .subscribe()
        }
    }

    override fun onResume() {
        super.onResume()

        remoteRenderer = VideoRenderer(binding.callRemoteView)
        appComponent.signalBroker.attachToVideoChat(this)
    }

    override fun onPause() {
        super.onPause()

        appComponent.signalBroker.detachFromVideoChat(this)
        remoteRenderer?.dispose()
        remoteRenderer = null
    }

    override var remoteRenderer: VideoRenderer? = null

    override fun setElapsedTime(seconds: Long) {
        binding.elapsedTimeSeconds = seconds
    }

    companion object {
        private val eglBase : EglBase by lazy { EglBase.create() }
    }
}