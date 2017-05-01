package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableField
import android.databinding.ObservableMap
import android.location.Location
import android.text.format.DateUtils
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.ImageMessageBody
import com.xianzhitech.ptt.data.LocationMessageBody
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageBody
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.TextMessageBody
import com.xianzhitech.ptt.ext.createCompositeObservable

abstract class MessageViewModel<out T : MessageBody>(val appComponent: AppComponent,
                                                     val message: Message) : ViewModel {
    @Suppress("UNCHECKED_CAST")
    val body: T
        get() = message.body as T

    val isMyMessage: Boolean
        get() = appComponent.signalBroker.peekUserId() == message.senderId

    val isSending: Boolean
        get() = message.remoteId.isNullOrBlank()

    val hasError: Boolean
        get() = message.error

    val displayTime: CharSequence
        get() = DateUtils.getRelativeTimeSpanString(message.sendTime.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

    open val text: CharSequence
        get() = body.toDisplayText(BaseApp.instance)

    open fun onClickMessage() {

    }

    override fun equals(other: Any?): Boolean {
        if (other is MessageViewModel<*>) {
            return message == other.message
        }

        return super.equals(other)
    }

    override fun hashCode(): Int {
        return message.hashCode()
    }
}

class UnknownMessageViewModel(appComponent: AppComponent, message: Message) : MessageViewModel<MessageBody>(appComponent, message)

class NotificationMessageViewModel(appComponent: AppComponent, message: Message, private val displayText: String? = null) : MessageViewModel<MessageBody>(appComponent, message) {
    override val text: CharSequence
        get() = displayText ?: super.text
}

abstract class MeaningfulMessageViewModel<out T : MessageBody>(appComponent: AppComponent, message: Message, private val isSingleRoom: Boolean)
    : MessageViewModel<T>(appComponent, message) {

    val displaysSender
        get() = !isSingleRoom && !isMyMessage
}


class TextMessageViewModel(appComponent: AppComponent, message: Message, isSingleRoom: Boolean)
    : MeaningfulMessageViewModel<TextMessageBody>(appComponent, message, isSingleRoom) {

    init {
        Preconditions.checkArgument(message.body is TextMessageBody && message.type == MessageType.TEXT)
    }
}

class ImageMessageViewModel(appComponent: AppComponent, message: Message, isSingleRoom: Boolean,
                            val progresses: ObservableMap<String, Int>,
                            private val navigator: Navigator) : MeaningfulMessageViewModel<ImageMessageBody>(appComponent, message, isSingleRoom) {
    init {
        Preconditions.checkArgument(message.body is ImageMessageBody && message.type == MessageType.IMAGE)
    }

    val progress: ObservableField<String>
        get() = createCompositeObservable(progresses) { message.localId?.let { progresses[it] }?.toString() ?: "0" }

    val isUploadingImage: Boolean
        get() = message.remoteId == null && message.error.not() && progresses.containsKey(message.localId)

    override fun onClickMessage() {
        navigator.navigateToImageViewer(body.url)
    }

    fun onClickRetry() {
        navigator.retrySendingImage(message)
    }

    interface Navigator {
        fun navigateToImageViewer(url: String)
        fun retrySendingImage(message: Message)
    }
}

class LocationMessageViewModel(appComponent: AppComponent, message: Message, isSingleRoom: Boolean,
                               private val navigator: Navigator)
    : MeaningfulMessageViewModel<LocationMessageBody>(appComponent, message, isSingleRoom) {

    init {
        Preconditions.checkArgument(message.body is LocationMessageBody)
    }

    val isEmpty: Boolean
        get() = body.isEmpty

    override val text: String
        get() = if (isEmpty) BaseApp.instance.getString(R.string.locating) else String.format("%.3f,%.3f", body.lat, body.lng)

    override fun onClickMessage() {
        navigator.navigateToMap(Location("gps").apply {
            accuracy = body.accuracy
            latitude = body.lat
            longitude = body.lng
            time = message.sendTime.time
        })
    }

    interface Navigator {
        fun navigateToMap(location: Location)
    }
}