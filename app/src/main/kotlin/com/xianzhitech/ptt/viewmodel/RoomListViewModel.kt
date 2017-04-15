package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.android.schedulers.AndroidSchedulers


class RoomListViewModel(private val appComponent: AppComponent,
                        private val navigator : Navigator) : LifecycleViewModel() {
    val roomViewModels = ObservableArrayList<RoomItemViewModel>()
    val roomMessages = ObservableArrayMap<String, Message>()
    val emptyViewVisible = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.storage.getAllRoomLatestMessage()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    roomMessages.clear()
                    roomMessages.putAll(it)
                }
                .bindToLifecycle()

        appComponent.storage.getAllRooms()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { emptyViewVisible.set(roomViewModels.isEmpty()) }
                .logErrorAndForget()
                .subscribe { rooms ->
                    roomViewModels.replaceAll(rooms.map { (room, name) -> RoomItemViewModel(room, name, roomMessages, navigator) })
                    emptyViewVisible.set(roomViewModels.isEmpty())
                }
                .bindToLifecycle()
    }

    interface Navigator : RoomItemViewModel.Navigator
}