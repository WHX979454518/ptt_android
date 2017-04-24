package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableField
import android.databinding.ObservableMap
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.data.ImageMessageBody
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.TextMessageBody
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.logErrorAndForget

abstract class MessageViewModel(val appComponent: AppComponent,
                                val message: Message) : ViewModel {
    val isMyMessage: Boolean
        get() = appComponent.signalBroker.peekUserId() == message.senderId

    val isSending: Boolean
        get() = message.remoteId.isNullOrBlank()

    fun onClickRetry() {
        appComponent.signalBroker.sendMessage(message)
                .toCompletable()
                .logErrorAndForget()
                .subscribe()
    }

    override fun equals(other: Any?): Boolean {
        if (other is MessageViewModel) {
            return message == other.message
        }

        return super.equals(other)
    }

    override fun hashCode(): Int {
        return message.hashCode()
    }
}

class UnknownMessageViewModel(appComponent: AppComponent, message: Message) : MessageViewModel(appComponent, message)

class NotificationMessageViewModel(appComponent: AppComponent, message: Message) : MessageViewModel(appComponent, message) {

    val text: CharSequence
        get() = message.body?.toDisplayText(BaseApp.instance) ?: ""
}


abstract class MeaningfulMessageViewModel(appComponent: AppComponent, message: Message, private val isSingleRoom: Boolean) : MessageViewModel(appComponent, message) {

    val displaysSender
        get() = !isSingleRoom && !isMyMessage
}


class TextMessageViewModel(appComponent: AppComponent, message: Message, isSingleRoom: Boolean) : MeaningfulMessageViewModel(appComponent, message, isSingleRoom) {

    init {
        Preconditions.checkArgument(message.body is TextMessageBody && message.type == MessageType.TEXT)
    }


    val text: String
        get() = (message.body as TextMessageBody).text

}

class ImageMessageViewModel(appComponent: AppComponent, message: Message, isSingleRoom: Boolean,
                            val progresses: ObservableMap<String, Int>) : MeaningfulMessageViewModel(appComponent, message, isSingleRoom) {
    init {
        Preconditions.checkArgument(message.body is ImageMessageBody && message.type == MessageType.IMAGE)
    }

    val url: String
        get() = (message.body as ImageMessageBody).url

    val progress: ObservableField<String>
        get() = createCompositeObservable(progresses) { message.localId?.let { progresses[it] }?.toString() ?: "0" }

    val isUploadingImage: Boolean
        get() = message.remoteId == null && message.error.isNullOrBlank() && progresses.containsKey(message.localId)
}