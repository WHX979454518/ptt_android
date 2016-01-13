package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.provider.*
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import kotlin.collections.forEach
import kotlin.collections.listOf

/**
 * Created by fanchao on 10/01/16.
 */
class RoomPresenter(private val signalProvider: SignalProvider,
                    private val authProvider: AuthProvider,
                    private val talkEngineProvider: TalkEngineProvider,
                    private val userRepository: UserRepository,
                    private val conversationRepository: ConversationRepository) : BasePresenter<RoomPresenterView>() {
    private var joinRoomSubscription: Subscription? = null

    private var activeConversationId: String? = null
    private var activeRoomSubscription: Subscription? = null
    private val activeRoom = BehaviorSubject.create<RoomDetails?>()
    private var activeTalkEngine: TalkEngine? = null


    override fun attachView(view: RoomPresenterView) {
        super.attachView(view)

        if (activeConversationId == null) {
            // Do nothing
        } else if (joinRoomSubscription != null) {
            // We're joining room
            view.showLoading(true)
        } else if (activeRoom.value != null) {
            // We've joined a room
            view.showLoading(false)
            showViewsRoom(listOf(view), activeRoom.value!!)
        }
    }

    fun requestMic() {
        activeConversationId?.let {
            if (activeRoom.value?.currentSpeaker != null) {
                // There's already a speaker speaking. Nothing we can do
                return
            }

            views.forEach { it.showRequestingMic(true) }
            signalProvider.requestMic(it)
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<Boolean>() {
                        override fun onError(e: Throwable) {
                            notifyViewsError(e)
                        }

                        override fun onNext(t: Boolean) {
                            views.forEach {
                                it.showRequestingMic(false)
                            }
                        }
                    })
        }
    }

    fun releaseMic() {
        activeConversationId?.let {
            signalProvider.releaseMic(it).subscribe()
            activeTalkEngine?.stopSend()
        }
    }

    /**
     * 请求退出房间.
     * 如果当前房间是标为重要的, 则不可退出
     */
    fun requestQuitCurrentRoom() {
        activeConversationId?.let {
            activeRoom.observeOnMainThread()
                    .first()
                    .subscribe {
                        it?.let {
                            if (it.conversation.important) {
                                views.forEach { view -> view.promptCurrentJoinedRoomIsImportant(it.conversation) }
                            } else {
                                signalProvider.quitConversation(it.conversation.id).subscribe()
                                activeRoomSubscription?.unsubscribe()
                                activeRoom.onNext(null)
                                activeConversationId = null
                                activeTalkEngine = activeTalkEngine?.let { it.dispose(); null }

                                views.forEach { view -> view.onRoomQuited(it.conversation) }
                            }
                        }
                    }

        }

        showViewsLoading(false)
        views.forEach { it.onRoomQuited(null) }
    }

    /**
     * 请求加入房间. 这里分几种情况:
     *  1. 当前已经加入了一个房间, 如果当前房间和这个房间一致, 那么告诉视图进入了这个房间
     *  2. 当前已经加入了一个非重要的房间, 但是当前房间和这个房间不一样, 提示用户即将切换房间
     *  3. 当前已经加入了一个重要的房间, 但是当前房间和这个房间不一样, 提示用户不能切换房间, 并强制切回重要房间
     *  4. 当前没有加入房间, 直接进入这个请求的房间
     *  5. 如果当前请求不是从会话列表发起（即没有房间ID）,则先向服务器查询房间ID, 然后进行1-4的判断.
     */
    fun requestJoinRoom(request: ConversationRequest, confirm: Boolean) {
        joinRoomSubscription?.unsubscribe()
        showViewsLoading(true)

        joinRoomSubscription = CompositeSubscription().apply {
            if (request is CreateConversationRequest) {
                add(signalProvider.createConversation(request)
                        .first()
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<Conversation>() {
                            override fun onError(e: Throwable) {
                                notifyViewsError(e)
                                showViewsLoading(false)
                                joinRoomSubscription = null
                            }

                            override fun onNext(t: Conversation) {
                                doJoinConversation(t.id, confirm)?.let { this.add(it) }
                            }
                        }))
            } else if (request is ConversationFromExisting) {
                if (request.conversationId == activeConversationId) {
                    // 如果当前已经有一个一样的房间, 那么看看有没有缓存的房间信息, 有就直接通知视图刷新, 否则就啥都不干, 因为这个信息等一下一定会拿得到
                    if (activeRoom.value != null) {
                        showViewsRoom(views, activeRoom.value!!)
                    }

                    showViewsLoading(false)
                    joinRoomSubscription = null
                } else {
                    doJoinConversation(request.conversationId, confirm)?.let { this.add(it) }
                }
            }
        }
    }

    private fun doJoinConversation(conversationId: String, confirm: Boolean): Subscription? {
        logd("Joining room: $conversationId, confirm: $confirm")
        val joinRoomAction: (RoomDetails?) -> Unit = {
            val willJoin: Boolean
            if (it == null) {
                willJoin = true
            } else if (!it.conversation.important) {
                willJoin = confirm

                if (!confirm) {
                    views.forEach { view -> view.promptConfirmSwitchingRoom(it.conversation) }
                }
            } else {
                views.forEach { view -> view.promptCurrentJoinedRoomIsImportant(it.conversation) }
                willJoin = false
            }

            logd("Will join room: $willJoin")

            if (willJoin) {
                // 加入请求的房间
                activeConversationId = conversationId
                activeRoomSubscription?.unsubscribe()
                activeTalkEngine = activeTalkEngine?.let {
                    it.dispose()
                    null
                }

                // 加入房间并订阅房间的相关信息（成员, 在线成员以及当前说话用户）
                activeRoomSubscription = signalProvider.joinConversation(conversationId)
                        .observeOnMainThread()
                        .flatMap { result ->
                            logd("Join room result: $result")

                            if (activeTalkEngine == null) {
                                activeTalkEngine = talkEngineProvider.createEngine().apply {
                                    connect(result.roomId, result.engineProperties)
                                }
                            }

                            views.forEach { view -> view.onRoomJoined(conversationId) }

                            Observable.combineLatest(
                                    conversationRepository.getConversation(result.conversationId),
                                    conversationRepository.getConversationMembers(conversationId),
                                    signalProvider.getActiveMemberIds(conversationId),
                                    signalProvider.getCurrentSpeakerId(conversationId).flatMap {
                                        val logonUser = authProvider.peekCurrentLogonUser()
                                        if (it == null) {
                                            Observable.just<Person>(null)
                                        } else if (it == logonUser?.id ?: null) {
                                            Observable.just(logonUser)
                                        } else {
                                            userRepository.getUser(it)
                                        }
                                    },
                                    { first, second, third, forth -> RoomDetails(first ?: throw StaticUserException(R.string.error_unable_to_get_room_info), second, third, forth) })
                        }
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<RoomDetails>() {
                            override fun onError(e: Throwable) {
                                notifyViewsError(e)
                                activeConversationId = null
                                activeRoom.onNext(null)
                            }

                            override fun onNext(t: RoomDetails) {
                                if (t.currentSpeaker?.id == authProvider.peekCurrentLogonUser()!!.id) {
                                    activeTalkEngine?.startSend()
                                } else {
                                    activeTalkEngine?.stopSend()
                                }

                                activeRoom.onNext(t)
                                showViewsRoom(views, t)
                            }
                        })

            }

            showViewsLoading(false)
            joinRoomSubscription = null
        }

        if (activeConversationId == null) {
            joinRoomAction(null)
            return null
        } else {
            return activeRoom.first().observeOnMainThread().subscribe(joinRoomAction)
        }

    }

    private fun showViewsRoom(views: Iterable<RoomPresenterView>, roomDetails: RoomDetails) {
        views.forEach {
            it.showRoom(roomDetails.conversation)
            it.showRoomMembers(roomDetails.members, roomDetails.activeMemberIds)
            it.showCurrentSpeaker(roomDetails.currentSpeaker,
                    roomDetails.currentSpeaker != null && roomDetails.currentSpeaker.id == authProvider.peekCurrentLogonUser()?.id)
        }
    }

    private data class RoomDetails(val conversation: Conversation,
                                   val members: List<Person>,
                                   val activeMemberIds: Collection<String>,
                                   val currentSpeaker: Person?)
}