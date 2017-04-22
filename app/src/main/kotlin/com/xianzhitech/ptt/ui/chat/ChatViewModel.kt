package com.xianzhitech.ptt.ui.chat

import android.content.Context
import android.databinding.ObservableField
import android.support.v7.util.SortedList
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.TextMessage
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.viewmodel.TopBannerViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit


class ChatViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val roomMessages: SortedList<Message>,
                    private val roomId: String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val room = ObservableField<Room>()
    val title = ObservableField<String>()

    val message = ObservableField<String>()
    val topBannerViewModel = addChildModel(TopBannerViewModel(appComponent, appContext, navigator))
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }
    val moreSelectionOpen = ObservableField(false)

    private var startMessageDate: Date? = null

    override fun onStart() {
        super.onStart()

        appComponent.storage.getRoomWithName(roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe {
                    room.set(it.orNull()?.first)
                    title.set(it.orNull()?.second)
                }

        val messages = appComponent.storage.getMessagesFrom(startMessageDate, roomId).share()

        messages.observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onMessageUpdated)
                .bindToLifecycle()

        messages.debounce(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .startWith(emptyList<Message>())
                .flatMapCompletable { appComponent.storage.markRoomAllMessagesRead(roomId).logErrorAndForget() }
                .logErrorAndForget()
                .subscribe()
                .bindToLifecycle()
    }

    private fun onMessageUpdated(messages: List<Message>) {
        roomMessages.addAll(messages)
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {
        navigator.navigateToWalkieTalkie(roomId)
    }

    fun onClickEmoji() {
        navigator.openEmojiDrawer()
    }

    fun onClickWalkieTalkie() {
    }

    fun onClickLocation() {

    }

    fun onClickAlbum() {

    }

    fun onClickVideo() {
        navigator.navigateToVideoChatPage(roomId)
    }

    fun onClickVoice() {

    }

    fun onClickCamera() {

    }

    fun onClickSend() {
        val msg = appComponent.signalBroker.createMessage(roomId, MessageType.TEXT, TextMessage(message.get()))
        message.set(null)
        appComponent.signalBroker.sendMessage(msg).toMaybe().logErrorAndForget().subscribe()
    }

    fun onClickMore() {
        moreSelectionOpen.set(!moreSelectionOpen.get())
    }


    interface Navigator : TopBannerViewModel.Navigator {
        fun navigateToWalkieTalkie(roomId: String)
        fun navigateToLatestMessageIfPossible()
        fun displayNoPermissionToWalkie()
        fun openEmojiDrawer()
        fun navigateToVideoChatPage(roomId: String)
    }

}