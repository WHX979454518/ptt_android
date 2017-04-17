package com.xianzhitech.ptt.viewmodel

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ext.combineLatest
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable


class ContactSelectionListViewModel(preselectedIds: List<String>,
                                    private val appComponent: AppComponent,
                                    private val showGroup: Boolean)
    : ModelListViewModel(selectable = true, preselectedIds = preselectedIds) {
    @Suppress("UNCHECKED_CAST")
    override val contactModels: Observable<List<NamedModel>>
        get() {
            return if (showGroup) {
                combineLatest(appComponent.storage.getAllGroups(), appComponent.storage.getAllUsers(), { groups, users ->
                    (groups + users) as List<NamedModel>
                })
            } else {
                appComponent.storage.getAllUsers() as Observable<List<NamedModel>>
            }
        }
}