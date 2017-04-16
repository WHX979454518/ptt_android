package com.xianzhitech.ptt.ui.chat

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.v7.util.SortedList
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.TextMessage
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.util.ObservableArrayList
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.viewmodel.TopBannerViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import java.util.*


class ChatViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val roomMessages : SortedList<Message>,
                    private val roomId: String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val message = ObservableField<String>()
    val roomViewModel = addChildModel(RoomViewModel(roomId, appComponent))
    val topBannerViewModel = addChildModel(TopBannerViewModel(appComponent, appContext, navigator))
    val title = createCompositeObservable(listOf(roomViewModel.roomName)) { roomViewModel.roomName.get() }
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }
    val walkieAvailable = ObservableBoolean()
    val moreSelectionOpen = ObservableField(false)

    private var startMessageDate: Date? = null

    override fun onStart() {
        super.onStart()

        val observable: Observable<Boolean> =
                Observable.combineLatest(
                        appComponent.signalBroker.currentUser,
                        appComponent.storage.getRoom(roomId),
                        BiFunction { _, room -> room.orNull()?.let { appComponent.signalBroker.hasRoomPermission(it) } ?: false }
                )

        observable.subscribe(walkieAvailable::set).bindToLifecycle()

        appComponent.storage.getMessagesFrom(startMessageDate, roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onMessageUpdated)
                .bindToLifecycle()
    }

    private fun onMessageUpdated(messages: List<Message>) {
        roomMessages.addAll(messages)
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {

    }

    fun onClickEmoji() {
        navigator.openEmojiDrawer()
    }

    fun onClickWalkieTalkie() {
        if (walkieAvailable.get().not()) {
            navigator.displayNoPermissionToWalkie()
        } else {
            navigator.navigateToWalkieTalkie(roomId)
        }
    }

    fun onClickLocation() {

    }

    fun onClickAlbum() {

    }

    fun onClickVideo() {

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
    }

}