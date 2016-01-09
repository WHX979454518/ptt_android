package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.presenter.base.PresenterView

/**
 * Created by fanchao on 9/01/16.
 */
class ConversationPresenter(private val broker: Broker) : BasePresenter<ConversationView>() {
    private var result: List<Broker.AggregateInfo<Conversation, String>> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                views.forEach { it.showConversationList(value) }
            }
        }

    init {
        broker.getConversationsWithMemberNames(3)
                .subscribe(object : GlobalSubscriber<List<Broker.AggregateInfo<Conversation, String>>>() {
                    override fun onError(e: Throwable) {
                        views.forEach { it.showError(e.message) }
                    }

                    override fun onNext(t: List<Broker.AggregateInfo<Conversation, String>>) {
                        result = t
                    }
                })
    }

    override fun attachView(view: ConversationView) {
        super.attachView(view)

        view.showConversationList(result)
    }
}

interface ConversationView : PresenterView {
    fun showConversationList(list: List<Broker.AggregateInfo<Conversation, String>>)
}
