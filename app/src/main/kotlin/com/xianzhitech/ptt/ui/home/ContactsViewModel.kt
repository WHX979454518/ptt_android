package com.xianzhitech.ptt.ui.home

import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.ContactGroup
import com.xianzhitech.ptt.data.ContactUser
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import rx.Single


class ContactsViewModel(private val appComponent: AppComponent,
                        private val contactsNavigator: Navigator) : ModelListViewModel() {

    val refreshing = ObservableBoolean()

    override val contactModels: Observable<List<com.xianzhitech.ptt.data.NamedModel>>
        get() = Observable.combineLatest(
                appComponent.storage.getAllGroups(),
                appComponent.storage.getAllUsers(),
                BiFunction { users, groups -> users + groups }
        )

    override fun onClickUser(user: ContactUser) {
        super.onClickUser(user)
        onClick(user)
    }

    override fun onClickGroup(group: ContactGroup) {
        super.onClickGroup(group)
        onClick(group)
    }

    private fun onClick(model: com.xianzhitech.ptt.data.NamedModel) {
        val roomResponse: Single<Room>
        if (model is Group) {
            roomResponse = appComponent.signalHandler.createRoom(groupIds = listOf(model.id))
        } else {
            roomResponse = appComponent.signalHandler.createRoom(userIds = listOf(model.id))
        }

        roomResponse
                .doOnLoadingState(loading::set)
                .observeOnMainThread()
                .subscribeSimple(contactsNavigator::navigateToChatRoom)
                .bindToLifecycle()
    }

    fun refresh() {
        appComponent.signalBroker.syncContacts()
                .doOnLoading(refreshing::set)
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget(contactsNavigator::displayContactSyncError)
                .subscribe(contactsNavigator::displayContactSyncSuccess)
    }

    interface Navigator {
        fun navigateToChatRoom(room: Room)
        fun displayContactSyncSuccess()
        fun displayContactSyncError(err : Throwable)
    }
}