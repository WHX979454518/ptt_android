package com.xianzhitech.ptt.ui.user

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ext.doOnLoadingState
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toLevelString
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import io.reactivex.android.schedulers.AndroidSchedulers


class UserDetailsViewModel(private val appComponent: AppComponent,
                           private val navigator: Navigator,
                           private val userId: String) : LifecycleViewModel() {

    val user = ObservableField<User>()
    val userLevel = createCompositeObservable(user) { user.get()?.priority?.toLevelString() }
    val loading = ObservableBoolean()
    val isMe = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.storage
                .getUser(userId)
                .doOnLoadingState(loading::set)
                .logErrorAndForget()
                .subscribe {
                    user.set(it.orNull())
                    isMe.set(appComponent.signalBroker.peekUserId() == it.orNull()?.id)
                }
                .bindToLifecycle()
    }

    fun onClickPhoneNumber() {
        if (user.get() != null && user.get().phoneNumber != null) {
            navigator.navigateToDialPhone(user.get().phoneNumber!!)
        }
    }

    fun onClickStartWalkieTalkie() {
        appComponent.signalBroker
                .createRoom(userIds = listOf(userId))
                .doOnLoadingState(loading::set)
                .toMaybe()
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget(Throwable::toast)
                .subscribe { room -> navigator.navigateToWalkieTalkiePage(room.id)}
                .bindToLifecycle()
    }

    fun onClickStartVideoChat() {
        appComponent.signalBroker
                .createRoom(userIds = listOf(userId))
                .doOnLoadingState(loading::set)
                .toMaybe()
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget(Throwable::toast)
                .subscribe { room -> navigator.navigateToVideoChatPage(room.id, false)}
                .bindToLifecycle()
    }

    interface Navigator {
        fun navigateToWalkieTalkiePage(roomId: String)
        fun navigateToVideoChatPage(roomId: String, audioOnly: Boolean)
        fun navigateToDialPhone(phoneNumber: String)
    }
}