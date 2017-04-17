package com.xianzhitech.ptt.viewmodel

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable


class RoomMemberListViewModel(private val appComponent: AppComponent,
                              val roomId: String) : ModelListViewModel() {
    override val contactModels: Observable<List<NamedModel>>
        get() = appComponent.storage.getRoom(roomId)
                .switchMap { room ->
                    if (room.isPresent) {
                        appComponent.storage.getRoomMembers(room.get())
                                .map { listOf(appComponent.signalBroker.currentUser.value.get()) + (it as List<NamedModel>) }
                    }
                    else {
                        Observable.just(emptyList())
                    }
                }
}