package com.xianzhitech.ptt.ui.call

import android.os.Bundle
import android.view.WindowManager
import com.xianzhitech.ptt.databinding.ActivityCallBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.service.handler.GroupChatView
import com.xianzhitech.ptt.ui.base.BaseActivity
import org.webrtc.EglBase
import org.webrtc.VideoRenderer

class CallActivity : BaseActivity(), GroupChatView {
    private lateinit var binding : ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callRemoteView.init(eglBase.eglBaseContext, null)
        binding.callEnd.setOnClickListener {
            appComponent.signalHandler.quitGroupChat()
            finish()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.callRemoteView.release()
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, isVideoChat: Boolean) {
        if (appComponent.signalHandler.groupChatRoomId == null || (appComponent.signalHandler.groupChatRoomId != roomId)) {
            appComponent.signalHandler.quitRoom()
            appComponent.signalHandler.startGroupChat(roomId)
        }
    }

    override fun onResume() {
        super.onResume()

        remoteRenderer = VideoRenderer(binding.callRemoteView)
        appComponent.signalHandler.attachToGroupChat(this)
    }

    override fun onPause() {
        super.onPause()

        appComponent.signalHandler.detachFromGroupChat(this)
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