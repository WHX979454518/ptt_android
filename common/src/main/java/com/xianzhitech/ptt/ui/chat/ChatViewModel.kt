package com.xianzhitech.ptt.ui.chat

import android.content.Context
import android.databinding.ObservableArrayMap
import android.databinding.ObservableField
import android.databinding.ObservableMap
import android.net.Uri
import android.support.v7.util.SortedList
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.data.ImageMessageBody
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.TextMessageBody
import com.xianzhitech.ptt.data.copy
import com.xianzhitech.ptt.data.isSingle
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.viewmodel.ImageMessageViewModel
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.viewmodel.MessageViewModel
import com.xianzhitech.ptt.viewmodel.TextMessageViewModel
import com.xianzhitech.ptt.viewmodel.TopBannerViewModel
import com.xianzhitech.ptt.viewmodel.UnknownMessageViewModel
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit


class ChatViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val roomMessages: SortedList<MessageViewModel>,
                    private val roomId: String,
                    private val navigator: Navigator) : LifecycleViewModel() {
    val room = ObservableField<Room>()
    val title = ObservableField<String>()

    val message = ObservableField<String>()
    val topBannerViewModel = addChildModel(TopBannerViewModel(appComponent, appContext, navigator))
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }
    val moreSelectionOpen = ObservableField(false)

    private var startMessageDate = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))

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
        roomMessages.addAll(messages.mapNotNull { it.toViewModel() })
        navigator.navigateToLatestMessageIfPossible()
    }

    fun onClickCall() {
        navigator.navigateToWalkieTalkie(roomId)
    }

    fun onClickEmoji() {
        navigator.openEmojiDrawer()
    }

    fun onClickWalkieTalkie() {
        navigator.navigateToWalkieTalkie(roomId)
    }

    fun onClickLocation() {

    }

    fun onClickAlbum() {
        navigator.navigateToPickAlbum()
    }

    fun onClickVideo() {
        navigator.navigateToVideoChatPage(roomId)
    }

    fun onClickVoice() {

    }

    fun onClickCamera() {
        navigator.navigateToCamera()
    }

    fun onClickSend() {
        val msg = appComponent.signalBroker.createMessage(roomId, MessageType.TEXT, TextMessageBody(message.get()))
        message.set(null)
        appComponent.signalBroker.sendMessage(msg).toMaybe().logErrorAndForget().subscribe()
    }

    fun onNewImage(image: Uri) {
        logger.i { "Got image $image" }

        val imageBody = ImageMessageBody(url = image.toString())
        val msg = appComponent.signalBroker.createMessage(roomId, MessageType.IMAGE, imageBody)
        val msgId = msg.localId!!
        IMAGE_UPLOAD_PROGRESS[msgId] = 0

        appComponent.storage.saveMessage(msg)
                .flatMap {
                    appComponent.signalBroker.uploadImage(image) { progress ->
                        AndroidSchedulers.mainThread().scheduleDirect {
                            if (IMAGE_UPLOAD_PROGRESS.containsKey(msgId)) {
                                IMAGE_UPLOAD_PROGRESS[msgId] = progress
                            }
                        }
                    }
                }
                .flatMapCompletable { uri ->
                    logger.i { "Got uploaded uri: $uri" }
                    val updatedMessage = msg.copy { setBody(imageBody.copy(url = uri.toString())) }
                    appComponent.signalBroker.sendMessage(updatedMessage)
                            .toCompletable()
                            .andThen(downloadImage(uri).logErrorAndForget())
                }
                .onErrorResumeNext {
                    logger.e(it) { "Error uploading image: $image" }
                    appComponent.storage.setMessageError(msgId, it.describeInHumanMessage(BaseApp.instance).toString())
                }
                .logErrorAndForget()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete { IMAGE_UPLOAD_PROGRESS.remove(msgId) }
                .subscribe()
    }

    fun onClickMore() {
        moreSelectionOpen.set(!moreSelectionOpen.get())
    }

    private fun downloadImage(url : String) : Completable {
        return Single.defer {
            val target = Glide.with(BaseApp.instance)
                    .load(url)
                    .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)

            Single.fromFuture(target)
        }.subscribeOn(Schedulers.io()).toCompletable()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Message.toViewModel() : MessageViewModel? {
        return when (type) {
            MessageType.TEXT -> TextMessageViewModel(appComponent, this, room.get()?.isSingle ?: false)
            MessageType.IMAGE -> ImageMessageViewModel(appComponent, this, room.get()?.isSingle ?: false, IMAGE_UPLOAD_PROGRESS)
            MessageType.NOTIFY_CREATE_ROOM -> null
            else -> UnknownMessageViewModel(appComponent, this)
        }
    }

    interface Navigator : TopBannerViewModel.Navigator {
        fun navigateToWalkieTalkie(roomId: String)
        fun navigateToLatestMessageIfPossible()
        fun displayNoPermissionToWalkie()
        fun openEmojiDrawer()
        fun navigateToVideoChatPage(roomId: String)
        fun navigateToPickAlbum()
        fun navigateToCamera()
    }

    companion object {
        // Map localId to progress
        val IMAGE_UPLOAD_PROGRESS: ObservableMap<String, Int> = ObservableArrayMap<String, Int>()
    }

}