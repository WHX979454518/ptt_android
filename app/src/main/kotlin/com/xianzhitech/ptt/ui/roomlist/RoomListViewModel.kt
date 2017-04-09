package com.xianzhitech.ptt.ui.roomlist

import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.doOnLoadingState
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.ui.base.LifecycleViewModel
import com.xianzhitech.ptt.util.ObservableArrayList
import rx.Observable
import rx.Single


class RoomListViewModel(private val appComponent: AppComponent) : LifecycleViewModel(), RoomItemViewModel.Navigator {
    val roomViewModels = ObservableArrayList<RoomItemViewModel>()
    val loading = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.roomRepository
                .getAllRooms()
                .observe()
                .switchMap { rooms ->
                    Observable.from(rooms)
                            .flatMap { room ->
                                appComponent.roomRepository.getRoomName(room.id).getAsync().zipWith(Single.just(room)) { name, room -> name to room }.toObservable()
                            }
                            .reduce(hashMapOf<String, RoomName>()) { map, (name, room) -> map.apply { this[room.id] = name!! } }
                            .map { rooms to it }
                }
                .doOnLoadingState(loading::set)
                .subscribe { (rooms, names) ->
                    roomViewModels.replaceAll(rooms.map { RoomItemViewModel(it, names[it.id]!!, this@RoomListViewModel) })
                }
                .bindToLifecycle()
    }

}