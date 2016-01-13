package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.base.PresenterView

/**
 * Created by fanchao on 10/01/16.
 */
interface RoomPresenterView : PresenterView {
    fun promptCurrentJoinedRoomIsImportant(currentRoom: Conversation)
    fun promptConfirmSwitchingRoom(newRoom: Conversation)

    fun onRoomQuited(conversation: Conversation?)
    fun onRoomJoined(conversationId: String)

    fun showRoom(room: Conversation)
    fun showRequestingMic(isRequesting: Boolean)
    fun showCurrentSpeaker(speaker: Person?, isSelf: Boolean)
    fun showRoomMembers(members: List<Person>, activeMemberIds: Collection<String>)

}

open class BaseRoomPresenterView : RoomPresenterView {
    override fun promptCurrentJoinedRoomIsImportant(currentRoom: Conversation) {
    }

    override fun promptConfirmSwitchingRoom(newRoom: Conversation) {
    }

    override fun onRoomQuited(conversation: Conversation?) {
    }

    override fun onRoomJoined(conversationId: String) {
    }

    override fun showRoom(room: Conversation) {
    }

    override fun showRequestingMic(isRequesting: Boolean) {
    }

    override fun showCurrentSpeaker(speaker: Person?, isSelf: Boolean) {
    }

    override fun showRoomMembers(members: List<Person>, activeMemberIds: Collection<String>) {
    }

    override fun showLoading(visible: Boolean) {
    }

    override fun showError(err: Throwable) {
    }

}