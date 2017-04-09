package com.xianzhitech.ptt.ui.home

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.doOnLoadingState
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import rx.Single


class ContactsViewModel(modelProvider: ModelProvider,
                        private val appComponent: AppComponent,
                        private val contactsNavigator: Navigator): ModelListViewModel(modelProvider) {

    override fun onClickItem(model: NamedModel) {
        val roomResponse : Single<Room>
        if (model is Group) {
            roomResponse = appComponent.signalHandler.createRoom(groupIds = listOf(model.id))
        }
        else {
            roomResponse = appComponent.signalHandler.createRoom(userIds = listOf(model.id))
        }

        roomResponse
                .doOnLoadingState(loading::set)
                .observeOnMainThread()
                .subscribeSimple(contactsNavigator::navigateToChatRoom)
                .bindToLifecycle()
    }

    interface Navigator {
        fun navigateToChatRoom(room: Room)
    }
}