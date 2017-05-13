package com.xianzhitech.ptt.viewmodel

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.ContactUser
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable


class RoomMemberListViewModel(private val appComponent: AppComponent,
                              private val navigator: Navigator,
                              val roomId: String) : ModelListViewModel() {
    override val contactModels: Observable<List<NamedModel>>
        get() = appComponent.storage.getRoom(roomId)
                .switchMap { room ->
                    if (room.isPresent) {
                        appComponent.storage.getRoomMembers(room.get())
                    }
                    else {
                        Observable.just(emptyList())
                    }
                }

    override fun onClickUser(user: ContactUser) {
        navigator.navigateToUserDetailsPage(user.id)
    }

    interface Navigator {
        fun navigateToUserDetailsPage(userId: String)
    }
}