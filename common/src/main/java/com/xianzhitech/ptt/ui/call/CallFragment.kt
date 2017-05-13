package com.xianzhitech.ptt.ui.call

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.FragmentCallBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import org.webrtc.EglBase
import org.webrtc.VideoRenderer


class CallFragment : BaseViewModelFragment<CallViewModel, FragmentCallBinding>(), CallViewModel.Navigator {
    override var remoteRenderer: VideoRenderer? = null

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentCallBinding {
        return FragmentCallBinding.inflate(inflater, container, false).apply {
            callRemoteView.init(eglBase.eglBaseContext, null)
            callBack.setOnClickListener {
                closeCallPage()
            }
        }
    }

    override fun onDestroyViewBinding() {
        dataBinding.callRemoteView.release()
        super.onDestroyViewBinding()
    }

    fun joinRoom(roomId: String, audioOnly : Boolean) {
        viewModel.joinRoom(roomId, audioOnly)
    }

    override fun onStart() {
        remoteRenderer = VideoRenderer(dataBinding.callRemoteView)

        super.onStart()
    }

    override fun onStop() {
        super.onStop()

        remoteRenderer?.dispose()
        remoteRenderer = null
    }

    override fun closeCallPage() {
        callbacks<Callbacks>()!!.closeCallPage()
    }

    override fun displayRoomNoLongerExistsPage() {
        Toast.makeText(context, R.string.error_room_not_exists, Toast.LENGTH_LONG).show()
        closeCallPage()
    }

    override fun onCreateViewModel(): CallViewModel {
        return CallViewModel(
                appComponent = appComponent,
                requestJoinRoomId = arguments.getString(ARG_JOIN_ROOM_ID),
                requestJoinAudioOnly = arguments.getBoolean(ARG_JOIN_ROOM_AUDIO_ONLY, false),
                navigator = this
        )
    }

    interface Callbacks {
        fun closeCallPage()
    }

    companion object {
        private val eglBase : EglBase by lazy { EglBase.create() }

        const val ARG_JOIN_ROOM_ID = "join_room_id"
        const val ARG_JOIN_ROOM_AUDIO_ONLY = "audio_only"
    }
}