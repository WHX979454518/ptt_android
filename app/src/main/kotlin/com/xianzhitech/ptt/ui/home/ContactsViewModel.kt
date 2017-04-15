package com.xianzhitech.ptt.ui.home

import android.databinding.ObservableBoolean
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.ContactGroup
import com.xianzhitech.ptt.data.ContactUser
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction


class ContactsViewModel(private val appComponent: AppComponent,
                        private val contactsNavigator: Navigator) : ModelListViewModel() {

    val refreshing = ObservableBoolean()

    override val contactModels: Observable<List<NamedModel>>
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

    private fun onClick(model: NamedModel) {
        val roomResponse: Single<Room>
        if (model is ContactGroup) {
            roomResponse = appComponent.signalBroker.createRoom(groupIds = listOf(model.id))
        } else {
            roomResponse = appComponent.signalBroker.createRoom(userIds = listOf(model.id))
        }

        roomResponse
                .doOnLoading(loading::set)
                .observeOn(AndroidSchedulers.mainThread())
                .toMaybe()
                .logErrorAndForget(contactsNavigator::displayCreateRoomError)
                .subscribe(contactsNavigator::navigateToChatRoom)
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
        fun displayContactSyncError(err: Throwable)
        fun displayCreateRoomError(err: Throwable)
    }
}