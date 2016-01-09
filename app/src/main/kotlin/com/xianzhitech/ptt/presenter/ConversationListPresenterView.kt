package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.presenter.base.PresenterView
import com.xianzhitech.ptt.repo.ConversationWithMemberNames

interface ConversationListPresenterView : PresenterView {
    fun showConversationList(result: List<ConversationWithMemberNames>)
}