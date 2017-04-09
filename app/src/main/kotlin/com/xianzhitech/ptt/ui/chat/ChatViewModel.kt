package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableArrayList
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.model.Message
import com.xianzhitech.ptt.ui.base.LifecycleViewModel
import org.json.JSONObject
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit


class ChatViewModel(private val appComponent: AppComponent,
                    private val roomId : String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val message = ObservableField<String>()
    val roomMessages = ObservableArrayList<Message>()
    val roomViewModel = addChildModel(RoomViewModel(roomId, appComponent))
    val title = createCompositeObservable(listOf(roomViewModel.roomName)) { roomViewModel.roomName.get() }

    val displaySend : Boolean = true
    val displayMore : Boolean = false


    override fun onStart() {
        super.onStart()

        roomMessages.clear()
        val currentUserId = appComponent.signalHandler.peekCurrentUserId!!

        (1..5000).mapTo(roomMessages) {
            Message(
                    id = it.toString(),
                    senderId = if (it % 3 == 0) currentUserId else "500001",
                    body = JSONObject().put("text", "这是消息 $it"),
                    type = "text",
                    read = false,
                    roomId = "123",
                    sendTime = System.currentTimeMillis()
            )
        }

        Observable.interval(10, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe {
                    val id = roomMessages.size
                    onReceiveNewMessage(
                            Message(
                                    id = id.toString(),
                                    senderId = if (id % 3 == 0) currentUserId else "500001",
                                    body = JSONObject().put("text", "这是消息wec $id"),
                                    type = "text",
                                    read = false,
                                    roomId = "123",
                                    sendTime = System.currentTimeMillis()
                            )
                    )
                }
    }

    private fun onReceiveNewMessage(msg: Message) {
        roomMessages.add(msg)
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {

    }

    fun onClickEmoji() {

    }

    fun onClickSend() {

    }

    fun onClickMore() {

    }

    interface Navigator {
        fun navigateToWalkieTalkie(roomId : String)
        fun navigateToLatestMessageIfPossible()
    }

}