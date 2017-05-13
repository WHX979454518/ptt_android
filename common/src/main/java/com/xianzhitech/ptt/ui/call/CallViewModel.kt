package com.xianzhitech.ptt.ui.call

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.media.AudioManager
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.handler.GroupChatView
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import org.webrtc.VideoRenderer


class CallViewModel(private val appComponent: AppComponent,
                    private var requestJoinRoomId : String?,
                    private val requestJoinAudioOnly: Boolean,
                    private val navigator: Navigator) : LifecycleViewModel(), GroupChatView {

    private val audioManager : AudioManager by lazy {
        BaseApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val speakerOn = ObservableBoolean(audioManager.isSpeakerphoneOn)
    val videoOn = ObservableBoolean(appComponent.signalBroker.videoChatVideoOn)
    val mute = ObservableBoolean(audioManager.isMicrophoneMute)
    val elapsedTime = ObservableField<String>()

    override fun onSaveState(out: Bundle) {
        super.onSaveState(out)

        out.putString(STATE_REQUEST_JOIN_ROOM_ID, requestJoinRoomId)
    }

    override fun onRestoreState(state: Bundle) {
        super.onRestoreState(state)

        requestJoinRoomId = state.getString(STATE_REQUEST_JOIN_ROOM_ID)
    }

    override fun onStart() {
        super.onStart()

        if (requestJoinRoomId != null) {
            joinRoom(requestJoinRoomId!!, requestJoinAudioOnly)
            requestJoinRoomId = null
        }
        else if (appComponent.signalBroker.peekVideoRoomId() == null) {
            navigator.displayRoomNoLongerExistsPage()
            return
        }

        speakerOn.set(audioManager.isSpeakerphoneOn)
        videoOn.set(appComponent.signalBroker.videoChatVideoOn)
        mute.set(audioManager.isMicrophoneMute)

        appComponent.signalBroker.attachToVideoChat(this)
    }

    override fun onStop() {
        appComponent.signalBroker.detachFromVideoChat(this)

        super.onStop()
    }

    override val remoteRenderer: VideoRenderer?
        get() = navigator.remoteRenderer

    override fun setElapsedTime(seconds: Long) {
        elapsedTime.set(DateUtils.formatElapsedTime(seconds))
    }

    fun onClickToggleSpeaker() {
        val speakerphoneOn = audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = speakerphoneOn.not()
        speakerOn.set(speakerphoneOn.not())
        Toast.makeText(BaseApp.instance, if (speakerOn.get()) R.string.speaker_on else R.string.speaker_off, Toast.LENGTH_SHORT).show()
    }

    fun onClickToggleMute() {
        val microphoneMute = audioManager.isMicrophoneMute
        audioManager.isMicrophoneMute = microphoneMute.not()
        mute.set(microphoneMute.not())
        Toast.makeText(BaseApp.instance, if (mute.get()) R.string.mute_on else R.string.mute_off, Toast.LENGTH_SHORT).show()
    }

    fun onClickQuit() {
        appComponent.signalBroker.quitVideoRoom()
        navigator.closeCallPage()
    }

    fun onClickBack() {
        navigator.closeCallPage()
    }

    fun onClickToggleVideo() {
        val on = appComponent.signalBroker.videoChatVideoOn
        appComponent.signalBroker.videoChatVideoOn = on.not()
        videoOn.set(on.not())
        Toast.makeText(BaseApp.instance, if (videoOn.get()) R.string.video_on else R.string.video_off, Toast.LENGTH_SHORT).show()
    }

    fun joinRoom(roomId: String, audioOnly: Boolean) {
        if (appComponent.signalBroker.currentVideoRoomId.value.orNull() != roomId) {
            appComponent.signalBroker.joinVideoRoom(roomId, audioOnly)
                    .observeOn(AndroidSchedulers.mainThread())
                    .logErrorAndForget {
                        it.toast()
                        navigator.closeCallPage()
                    }
                    .subscribe()
            videoOn.set(audioOnly.not())
        }
        else if (appComponent.signalBroker.videoChatVideoOn != audioOnly.not()) {
            appComponent.signalBroker.videoChatVideoOn = audioOnly.not()
            videoOn.set(audioOnly.not())
        }
    }

    interface Navigator {
        val remoteRenderer : VideoRenderer?
        fun displayRoomNoLongerExistsPage()
        fun closeCallPage()

    }
    companion object {
        private const val STATE_REQUEST_JOIN_ROOM_ID = "request_join_room"

    }
}