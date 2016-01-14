package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.RoomWithMemberNames
import rx.Subscription
import rx.subjects.BehaviorSubject
import kotlin.collections.emptyList
import kotlin.collections.forEach

/**
 * Created by fanchao on 9/01/16.
 */
class ConversationListPresenter(private val roomRepository: RoomRepository) : BasePresenter<ConversationListPresenterView>() {
    private val resultSubject = BehaviorSubject.create<List<RoomWithMemberNames>>(emptyList())
    private var subscription: Subscription? = null

    override fun attachView(view: ConversationListPresenterView) {
        super.attachView(view)

        if (subscription == null) {
            subscription = roomRepository.getRoomsWithMemberNames(3)
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<List<RoomWithMemberNames>>() {
                        override fun onError(e: Throwable) {
                            notifyViewsError(e)
                        }

                        override fun onNext(t: List<RoomWithMemberNames>) {
                            resultSubject.onNext(t)
                            views.forEach { it.showConversationList(t) }
                        }
                    })
        }

        resultSubject.value?.let { view.showConversationList(it) }
    }

    override fun detachView(view: ConversationListPresenterView) {
        super.detachView(view)

        if (views.isEmpty()) {
            subscription = subscription?.let { it.unsubscribe(); null }
        }
    }
}

