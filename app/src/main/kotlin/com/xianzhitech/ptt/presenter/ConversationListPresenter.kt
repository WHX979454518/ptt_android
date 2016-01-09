package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.ConversationWithMemberNames
import kotlin.collections.emptyList
import kotlin.collections.forEach

/**
 * Created by fanchao on 9/01/16.
 */
class ConversationListPresenter(private val conversationRepository: ConversationRepository) : BasePresenter<ConversationListPresenterView>() {
    private var result: List<ConversationWithMemberNames> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                views.forEach { it.showConversationList(value) }
            }
        }

    init {
        conversationRepository.getConversationsWithMemberNames(3)
                .subscribe(object : GlobalSubscriber<List<ConversationWithMemberNames>>() {
                    override fun onError(e: Throwable) {
                        views.forEach { it.showError(e.message) }
                    }

                    override fun onNext(t: List<ConversationWithMemberNames>) {
                        result = t
                    }
                })
    }

    override fun attachView(view: ConversationListPresenterView) {
        super.attachView(view)

        view.showConversationList(result)
    }
}

