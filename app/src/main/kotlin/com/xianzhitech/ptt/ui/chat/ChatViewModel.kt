package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.Message
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.util.ObservableArrayList
import org.json.JSONObject
import java.util.*


class ChatViewModel(private val appComponent: AppComponent,
                    private val roomId : String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val message = ObservableField<String>()
    val roomMessages = ObservableArrayList<Message>()
    val roomViewModel = addChildModel(RoomViewModel(roomId, appComponent))
    val title = createCompositeObservable(listOf(roomViewModel.roomName)) { roomViewModel.roomName.get() }
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }


    override fun onStart() {
        super.onStart()

        appComponent.messageRepository
                .getAllMessages(roomId, null, 512)
                .observe()
                .observeOnMainThread()
                .subscribe(this::onMessageUpdated)
                .bindToLifecycle()
    }

    private fun onMessageUpdated(messages : List<Message>) {
        roomMessages.replaceAll(messages)
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {
    }

    fun onClickEmoji() {

    }

    fun onClickSend() {
        val msg = Message(
                id = UUID.randomUUID().toString(),
                remoteId = null,
                senderId = appComponent.signalHandler.peekCurrentUserId!!,
                roomId = roomId,
                sendTime = System.currentTimeMillis(),
                read = true,
                type = "text",
                body = JSONObject().put("text", message.get())
        )

        appComponent.signalHandler.sendMessage(msg).subscribeSimple()
        message.set("")
    }

    fun onClickMore() {

    }

    interface Navigator {
        fun navigateToWalkieTalkie(roomId : String)
        fun navigateToLatestMessageIfPossible()
    }

}