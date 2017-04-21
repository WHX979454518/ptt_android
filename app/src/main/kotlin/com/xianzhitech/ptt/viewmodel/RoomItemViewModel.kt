package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableMap
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.MessageWithSender
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.RoomInfo
import com.xianzhitech.ptt.data.RoomWithMembersAndName
import com.xianzhitech.ptt.data.displayText
import com.xianzhitech.ptt.ext.createCompositeObservable


class RoomItemViewModel(val appComponent: AppComponent,
                        val room: RoomWithMembersAndName,
                        val roomName: String,
                        val roomMessage: ObservableMap<String, MessageWithSender>,
                        val roomInfo: ObservableMap<String, RoomInfo>,
                        val unreadMessageCount : ObservableMap<String, Int>,
                        val navigator: Navigator) : ViewModel {

    val hasNewMessage = createCompositeObservable(unreadMessageCount) {
        val count = unreadMessageCount[room.room.id]
        if (count == null) {
            false
        } else {
            count > 0
        }
    }

    val latestMessage = createCompositeObservable(roomMessage) {
        val msg = roomMessage[room.room.id]
        val name = if (appComponent.signalBroker.peekUserId() == msg?.user?.id) {
            App.instance.getString(R.string.me)
        } else {
            msg?.user?.name
        }

        msg?.let { "$name: ${it.message.displayText}" }
    }

    fun onClickRoom() {
        navigator.navigateToRoom(room.room)
    }

    fun onLongClickRoom() {

    }

    interface Navigator {
        fun navigateToRoom(room: Room)
    }
}