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
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.ImageMessageBody
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.MessageWithSender
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.TextMessageBody
import com.xianzhitech.ptt.data.copy
import com.xianzhitech.ptt.data.isSingle
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.loadImageAsBase64
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.viewmodel.ImageMessageViewModel
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.viewmodel.MessageViewModel
import com.xianzhitech.ptt.viewmodel.NotificationMessageViewModel
import com.xianzhitech.ptt.viewmodel.TextMessageViewModel
import com.xianzhitech.ptt.viewmodel.TopBannerViewModel
import com.xianzhitech.ptt.viewmodel.UnknownMessageViewModel
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class ChatViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val roomMessages: SortedList<MessageViewModel>,
                    private val roomId: String,
                    private val navigator: Navigator) : LifecycleViewModel(), ImageMessageViewModel.Navigator {
    val room = ObservableField<Room>()
    val title = ObservableField<String>()

    val message = ObservableField<String>()
    val topBannerViewModel = addChildModel(TopBannerViewModel(appComponent, appContext, navigator))
    val displaySendButton = createCompositeObservable(message) { message.get()?.isNotEmpty() ?: false }
    val displayMoreButton = createCompositeObservable(message) { message.get().isNullOrEmpty() }
    val moreSelectionOpen = ObservableField(false)

    val progresses = ObservableArrayMap<String, Int>()

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
                .startWith(emptyList<MessageWithSender>())
                .flatMapCompletable { appComponent.storage.markRoomAllMessagesRead(roomId).logErrorAndForget() }
                .logErrorAndForget()
                .subscribe()
                .bindToLifecycle()

        IMAGE_UPLOAD_PROGRESS
                .subscribe {
                    progresses.clear()
                    progresses.putAll(it)
                }
                .bindToLifecycle()
    }

    private fun onMessageUpdated(messages: List<MessageWithSender>) {
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

    override fun navigateToImageViewer(url: String) {
        navigator.navigateToImageViewer(url)
    }

    override fun retrySendingImage(message: Message) {
        doSendImageMessage(message).logErrorAndForget().subscribe()
    }

    private fun doSendImageMessage(msg: Message): Completable {
        val message = msg.copy { setError(false) }
        val msgId = message.localId!!
        IMAGE_UPLOAD_PROGRESS.value[msgId] = 0
        notifyProgress()

        val imageBody = message.body as ImageMessageBody

        return appComponent.storage.saveMessage(message)
                .flatMap {
                    appComponent.signalBroker.uploadImage(Uri.parse(imageBody.url)) { progress ->
                        AndroidSchedulers.mainThread().scheduleDirect {
                            if (IMAGE_UPLOAD_PROGRESS.value.containsKey(msgId)) {
                                IMAGE_UPLOAD_PROGRESS.value[msgId] = progress
                                notifyProgress()
                            }
                        }
                    }
                }
                .flatMapCompletable { uri ->
                    logger.i { "Got uploaded uri: $uri" }
                    val updatedMessage = message.copy { setBody(imageBody.copy(url = uri.toString())) }
                    appComponent.signalBroker.sendMessage(updatedMessage).toCompletable()
                }
                .onErrorResumeNext {
                    logger.e(it) { "Error uploading image: ${imageBody.url}" }
                    appComponent.storage.setMessageError(msgId, true)
                }
                .logErrorAndForget()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    IMAGE_UPLOAD_PROGRESS.value.remove(msgId)
                    notifyProgress()
                }
    }

    fun onNewImage(image: Uri) {
        logger.i { "Got image $image" }

        loadImageAsBase64(image.toString(), 600, 600)
                .onErrorReturn {
                    logger.e(it) { "Error generating thumbnail" }
                    ""
                }
                .flatMapCompletable {
                    val imageBody = ImageMessageBody(url = image.toString(), thumbnail = it)
                    val msg = appComponent.signalBroker.createMessage(roomId, MessageType.IMAGE, imageBody)

                    doSendImageMessage(msg)
                }
                .subscribe()
    }

    fun onClickMore() {
        moreSelectionOpen.set(!moreSelectionOpen.get())
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun MessageWithSender.toViewModel(): MessageViewModel? {
        return when (message.type) {
            MessageType.TEXT -> TextMessageViewModel(appComponent, message, room.get()?.isSingle ?: false)
            MessageType.IMAGE -> ImageMessageViewModel(appComponent, message, room.get()?.isSingle ?: false, progresses, this@ChatViewModel)
            MessageType.NOTIFY_JOIN_ROOM -> NotificationMessageViewModel(appComponent, message,
                    BaseApp.instance.getString(R.string.user_join_walkie, user?.name ?: ""))
            MessageType.NOTIFY_QUIT_ROOM -> NotificationMessageViewModel(appComponent, message,
                    BaseApp.instance.getString(R.string.user_quit_walkie, user?.name ?: ""))
            else -> UnknownMessageViewModel(appComponent, message)
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
        fun navigateToImageViewer(url: String)
    }

    companion object {
        // Map localId to progress
        val IMAGE_UPLOAD_PROGRESS: BehaviorSubject<ConcurrentHashMap<String, Int>> = BehaviorSubject.createDefault(ConcurrentHashMap())

        private fun notifyProgress() {
            IMAGE_UPLOAD_PROGRESS.onNext(IMAGE_UPLOAD_PROGRESS.value)
        }
    }

}