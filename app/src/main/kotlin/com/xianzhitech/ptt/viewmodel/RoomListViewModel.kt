package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.android.schedulers.AndroidSchedulers


class RoomListViewModel(private val appComponent: AppComponent) : LifecycleViewModel(), RoomItemViewModel.Navigator {
    val roomViewModels = ObservableArrayList<RoomItemViewModel>()
    val roomMessages = ObservableArrayMap<String, Message>()
    val loading = ObservableBoolean()

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
                .logErrorAndForget()
                .doOnLoading(loading::set)
                .subscribe { rooms ->
                    val newViewModels = rooms.map { (room, name) -> RoomItemViewModel(room, name, roomMessages, this) }
                    roomViewModels.replaceAll(newViewModels)
                }
                .bindToLifecycle()
    }

}