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

    fun showRoom(room: Conversation)
    fun showRequestingMic(isRequesting: Boolean)
    fun showCurrentSpeaker(speaker: Person?, isSelf: Boolean)
    fun showRoomMembers(members: List<Person>, activeMemberIds: Collection<String>)
}