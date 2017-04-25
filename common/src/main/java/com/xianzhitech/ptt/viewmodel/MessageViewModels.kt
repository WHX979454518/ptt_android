package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableField
import android.databinding.ObservableMap
import android.text.format.DateUtils
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.data.ImageMessageBody
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.TextMessageBody
import com.xianzhitech.ptt.ext.createCompositeObservable

abstract class MessageViewModel(val appComponent: AppComponent,
                                val message: Message) : ViewModel {
    val isMyMessage: Boolean
        get() = appComponent.signalBroker.peekUserId() == message.senderId

    val isSending: Boolean
        get() = message.remoteId.isNullOrBlank()

    val hasError: Boolean
        get() = message.error

    val displayTime: CharSequence
        get() = DateUtils.getRelativeTimeSpanString(message.sendTime.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

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

class NotificationMessageViewModel(appComponent: AppComponent, message: Message, private val displayText: String? = null) : MessageViewModel(appComponent, message) {

    val text: CharSequence
        get() = message.body?.toDisplayText(BaseApp.instance) ?: displayText ?: ""
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
                            val progresses: ObservableMap<String, Int>,
                            private val navigator: Navigator) : MeaningfulMessageViewModel(appComponent, message, isSingleRoom) {
    init {
        Preconditions.checkArgument(message.body is ImageMessageBody && message.type == MessageType.IMAGE)
    }

    val progress: ObservableField<String>
        get() = createCompositeObservable(progresses) { message.localId?.let { progresses[it] }?.toString() ?: "0" }

    val isUploadingImage: Boolean
        get() = message.remoteId == null && message.error.not() && progresses.containsKey(message.localId)

    fun onClickImage() {
        navigator.navigateToImageViewer((message.body as ImageMessageBody).url)
    }

    fun onClickRetry() {
        navigator.retrySendingImage(message)
    }

    interface Navigator {
        fun navigateToImageViewer(url: String)
        fun retrySendingImage(message: Message)
    }
}