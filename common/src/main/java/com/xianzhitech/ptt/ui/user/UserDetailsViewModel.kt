package com.xianzhitech.ptt.ui.user

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.doOnLoadingState
import com.xianzhitech.ptt.ext.fromOptional
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toLevelString
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel


class UserDetailsViewModel(private val appComponent: AppComponent,
                           private val navigator: Navigator,
                           private val userId: String) : LifecycleViewModel() {

    val user = ObservableField<User>()
    val userLevel = createCompositeObservable(user) { user.get()?.priority?.toLevelString() }
    val loading = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.storage
                .getUser(userId)
                .doOnLoadingState(loading::set)
                .logErrorAndForget()
                .subscribe(user::fromOptional)
                .bindToLifecycle()
    }

    fun onClickPhoneNumber() {
        if (user.get() != null && user.get().phoneNumber != null) {
            navigator.navigateToDialPhone(user.get().phoneNumber!!)
        }
    }

    fun onClickStartWalkieTalkie() {
//        appComponent.signalHandler
//                .createRoom(emptyList(), listOf(userId))
//                .map(Room::id)
//                .observeOnMainThread()
//                .doOnLoadingState(loading::set)
//                .subscribeSimple(navigator::navigateToWalkieTalkiePage)
//                .bindToLifecycle()
    }

    fun onClickStartVideoChat() {
//        appComponent.signalHandler
//                .createRoom(emptyList(), listOf(userId))
//                .map(Room::id)
//                .observeOnMainThread()
//                .doOnLoadingState(loading::set)
//                .subscribeSimple(navigator::navigateToVideoChatPage)
//                .bindToLifecycle()
    }

    interface Navigator {
        fun navigateToWalkieTalkiePage(roomId: String)
        fun navigateToVideoChatPage(roomId: String)
        fun navigateToDialPhone(phoneNumber: String)
    }
}