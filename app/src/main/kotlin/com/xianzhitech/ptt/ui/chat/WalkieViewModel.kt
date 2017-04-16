package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toOptional
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.util.ObservableArrayList
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import io.reactivex.android.schedulers.AndroidSchedulers


class WalkieViewModel(val roomId: String,
                      val appComponent: AppComponent,
                      val needsRoomMembers: Boolean = true) : LifecycleViewModel() {
    val roomName = ObservableField<String>()
    val roomMembers = ObservableArrayList<User>()
    val room = ObservableField<Room>()

    override fun onStart() {
        super.onStart()

        appComponent.storage
                .getRoomWithName(roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe {
                    roomName.set(it.orNull()?.second)
                    room.set(it.orNull()?.first)
                }

        if (needsRoomMembers) {
            appComponent.roomRepository
                    .getRoomMembers(roomId)
                    .observe()
                    .subscribe(roomMembers::replaceAll)
                    .bindToLifecycle()
        }
    }
}