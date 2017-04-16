package com.xianzhitech.ptt.ui.chat

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.common.base.Optional
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.without
import com.xianzhitech.ptt.model.Message
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.util.ObservableArrayList
import com.xianzhitech.ptt.viewmodel.TopBannerViewModel
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import org.json.JSONObject
import java.util.*


class ChatViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val roomId: String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val message = ObservableField<String>()
    val roomMessages = ObservableArrayList<Message>()
    val roomViewModel = addChildModel(RoomViewModel(roomId, appComponent))
    val topBannerViewModel = addChildModel(TopBannerViewModel(appComponent, appContext, navigator))
    val title = createCompositeObservable(listOf(roomViewModel.roomName)) { roomViewModel.roomName.get() }
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }
    val callAvailable = ObservableBoolean()

    private var startMessageId: String? = null

    override fun onStart() {
        super.onStart()

        val observable: Observable<Boolean> =
                Observable.combineLatest(
                        appComponent.signalBroker.currentUser,
                        appComponent.storage.getRoom(roomId),
                        BiFunction { _, room -> room.orNull()?.let { appComponent.signalBroker.hasRoomPermission(it) } ?: false }
                )

        observable.subscribe(callAvailable::set).bindToLifecycle()

        appComponent.messageRepository
                .getAllMessages(roomId, null, 512)
                .observe()
                .observeOnMainThread()
                .subscribe(this::onMessageUpdated)
                .bindToLifecycle()
    }

    private fun onMessageUpdated(messages: List<Message>) {
        roomMessages.replaceAll(messages)
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {
        if (callAvailable.get().not()) {
            navigator.displayNoPermissionToCall()
        } else {
            navigator.navigateToWalkieTalkie(roomId)
        }
    }

    fun onClickEmoji() {
        navigator.openEmojiDrawer()
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
        navigator.openMoreDrawer()
    }

    interface Navigator : TopBannerViewModel.Navigator {
        fun navigateToWalkieTalkie(roomId: String)
        fun navigateToLatestMessageIfPossible()
        fun displayNoPermissionToCall()
        fun openMoreDrawer()
        fun openEmojiDrawer()
    }

}