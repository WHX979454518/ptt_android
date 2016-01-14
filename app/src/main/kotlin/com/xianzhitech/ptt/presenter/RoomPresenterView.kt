package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.presenter.base.PresenterView

/**
 * Created by fanchao on 10/01/16.
 */
interface RoomPresenterView : PresenterView {
    fun promptCurrentJoinedRoomIsImportant(currentRoom: Room)
    fun promptConfirmSwitchingRoom(newRoom: Room)

    fun onRoomQuited(room: Room?)
    fun onRoomJoined(conversationId: String)

    fun showRoom(room: Room)
    fun showRequestingMic(isRequesting: Boolean)
    fun showCurrentSpeaker(speaker: User?, isSelf: Boolean)
    fun showRoomMembers(members: List<User>, activeMemberIds: Collection<String>)

}

open class BaseRoomPresenterView : RoomPresenterView {
    override fun promptCurrentJoinedRoomIsImportant(currentRoom: Room) {
    }

    override fun promptConfirmSwitchingRoom(newRoom: Room) {
    }

    override fun onRoomQuited(room: Room?) {
    }

    override fun onRoomJoined(conversationId: String) {
    }

    override fun showRoom(room: Room) {
    }

    override fun showRequestingMic(isRequesting: Boolean) {
    }

    override fun showCurrentSpeaker(speaker: User?, isSelf: Boolean) {
    }

    override fun showRoomMembers(members: List<User>, activeMemberIds: Collection<String>) {
    }

    override fun showLoading(visible: Boolean) {
    }

    override fun showError(err: Throwable) {
    }

}