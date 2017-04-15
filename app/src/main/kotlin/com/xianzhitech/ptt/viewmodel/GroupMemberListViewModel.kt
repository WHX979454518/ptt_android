package com.xianzhitech.ptt.viewmodel

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable


class GroupMemberListViewModel(private val appComponent: AppComponent,
                               private val groupId : String) : ModelListViewModel() {
    override val contactModels: Observable<List<NamedModel>>
        get() = appComponent.storage.getGroups(listOf(groupId))
                .switchMap { groups ->
                    groups.firstOrNull()?.let { appComponent.storage.getUsers(it.memberIds) } ?: Observable.empty()
                }
                .map { listOf(appComponent.signalBroker.currentUser.value.get()) + (it as List<NamedModel>) }
}