package com.xianzhitech.ptt.ui.roomlist

import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.ui.util.ViewModel


class RoomItemViewModel(val room : RoomModel,
                        val roomName: RoomName,
                        val navigator : Navigator) : ViewModel {

    fun onClickRoom() {

    }

    fun onLongClickRoom() {

    }

    interface Navigator {
    }
}