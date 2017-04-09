package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.ui.base.LifecycleViewModel
import com.xianzhitech.ptt.util.ObservableArrayList


class RoomViewModel(val roomId : String,
                    val appComponent: AppComponent,
                    val needsRoom : Boolean = true,
                    val needsRoomMembers : Boolean = true) : LifecycleViewModel() {
    val roomName = ObservableField<RoomName>()
    val roomMembers = ObservableArrayList<User>()
    val room = ObservableField<RoomModel>()

    override fun onStart() {
        super.onStart()

        if (needsRoom) {
            appComponent.roomRepository
                    .getRoom(roomId)
                    .observe()
                    .subscribe(room::set)
                    .bindToLifecycle()
        }

        appComponent.roomRepository
                .getRoomName(roomId, excludeUserIds = listOf(appComponent.signalHandler.peekCurrentUserId))
                .observe()
                .subscribe(roomName::set)
                .bindToLifecycle()

        if (needsRoomMembers) {
            appComponent.roomRepository
                    .getRoomMembers(roomId)
                    .observe()
                    .subscribe(roomMembers::replaceAll)
                    .bindToLifecycle()
        }
    }
}