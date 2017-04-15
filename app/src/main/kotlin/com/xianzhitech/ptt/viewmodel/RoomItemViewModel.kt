package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableMap
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.Room


class RoomItemViewModel(val room: Room,
                        val roomName: String,
                        val roomMessage: ObservableMap<String, Message>,
                        val navigator: Navigator) : ViewModel {

    fun onClickRoom() {
        navigator.navigateToRoom(room)
    }

    fun onLongClickRoom() {

    }

    interface Navigator {
        fun navigateToRoom(room: Room)
    }
}