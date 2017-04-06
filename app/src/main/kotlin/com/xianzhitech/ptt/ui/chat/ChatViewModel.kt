package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableArrayList
import android.databinding.ObservableField
import com.xianzhitech.ptt.model.Message
import com.xianzhitech.ptt.service.handler.SignalServiceHandler
import com.xianzhitech.ptt.ui.base.LifecycleViewModel
import org.json.JSONObject
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit


class ChatViewModel(private val signalServiceHandler: SignalServiceHandler) : LifecycleViewModel() {
    val message = ObservableField<String>()
    val roomMessages = ObservableArrayList<Message>()

    val displaySend : Boolean = true
    val displayMore : Boolean = false

    override fun onStart() {
        super.onStart()

        roomMessages.clear()
        val currentUserId = signalServiceHandler.peekCurrentUserId!!

        (1..5000).mapTo(roomMessages) {
            Message(
                    id = it.toString(),
                    senderId = if (it % 3 == 0) currentUserId else "500001",
                    body = JSONObject().put("text", "This is text message $it"),
                    type = "text",
                    read = false,
                    roomId = "123",
                    sendTime = System.currentTimeMillis()
            )
        }

        Observable.interval(10, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe {
                    val id = roomMessages.size
                    roomMessages.add(
                            Message(
                                    id = id.toString(),
                                    senderId = if (id % 3 == 0) currentUserId else "500001",
                                    body = JSONObject().put("text", "This is text message $id"),
                                    type = "text",
                                    read = false,
                                    roomId = "123",
                                    sendTime = System.currentTimeMillis()
                            )
                    )
                }
    }

    fun onClickCall() {

    }

    fun onClickEmoji() {

    }

    fun onClickSend() {

    }

    fun onClickMore() {

    }

}