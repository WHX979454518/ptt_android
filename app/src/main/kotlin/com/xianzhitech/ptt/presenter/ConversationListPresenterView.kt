package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.presenter.base.PresenterView
import com.xianzhitech.ptt.repo.RoomWithMemberNames

interface ConversationListPresenterView : PresenterView {
    fun showConversationList(result: List<RoomWithMemberNames>)
}