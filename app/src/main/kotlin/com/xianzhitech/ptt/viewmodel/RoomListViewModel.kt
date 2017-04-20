package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.RoomInfo
import com.xianzhitech.ptt.ext.combineLatest
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.android.schedulers.AndroidSchedulers


class RoomListViewModel(private val appComponent: AppComponent,
                        private val navigator : Navigator) : LifecycleViewModel() {
    val roomViewModels = ObservableArrayList<RoomItemViewModel>()
    val roomLatestMessages = ObservableArrayMap<String, Message>()
    val roomInfo = ObservableArrayMap<String, RoomInfo>()
    val emptyViewVisible = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        combineLatest(
                appComponent.storage.getAllRoomLatestMessage(),
                appComponent.storage.getAllRooms(),
                { messages, rooms -> messages to rooms })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (messages, rooms) ->
                    val sortedRooms = rooms.sortedBy { messages[it.first.id]?.sendTime }

                    roomViewModels.replaceAll(sortedRooms.map { (room, name) ->
                        RoomItemViewModel(room, name, roomLatestMessages, roomInfo, navigator) }
                    )

                    roomLatestMessages.clear()
                    roomLatestMessages.putAll(messages)

                    emptyViewVisible.set(roomViewModels.isEmpty())
                }
                .bindToLifecycle()

        appComponent.storage.getAllRoomInfo()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    roomInfo.clear()
                    roomInfo.putAll(it.associateBy(RoomInfo::roomId))
                }
                .bindToLifecycle()
    }

    interface Navigator : RoomItemViewModel.Navigator
}