package com.xianzhitech.ptt.ui.user

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.ContentViewEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.LifecycleViewModel
import com.xianzhitech.ptt.util.withUser


class UserDetailsViewModel(private val appComponent: AppComponent,
                           private val navigator: Navigator,
                           private val userId: String) : LifecycleViewModel() {

    val user = ObservableField<User>()
    val userLevel = createCompositeObservable(user) { user.get()?.priority?.toLevelString() }
    val loading = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.userRepository
                .getUser(userId)
                .observe()
                .doOnLoadingState(loading::set)
                .doOnNext {
                    if (it != null) {
                        Answers.getInstance().logContentView(ContentViewEvent().apply {
                            withUser(appComponent.signalHandler.peekCurrentUserId, appComponent.signalHandler.currentUserCache.value)
                            putContentType("user-details")
                            putContentId(userId)
                        })
                    }
                }
                .subscribeSimple(user::set)
                .bindToLifecycle()
    }

    fun onClickPhoneNumber() {
        if (user.get() != null && user.get().phoneNumber != null) {
            navigator.navigateToDialPhone(user.get().phoneNumber!!)
        }
    }

    fun onClickStartWalkieTalkie() {
        appComponent.signalHandler
                .createRoom(emptyList(), listOf(userId))
                .map(Room::id)
                .observeOnMainThread()
                .doOnLoadingState(loading::set)
                .subscribeSimple(navigator::navigateToWalkieTalkiePage)
                .bindToLifecycle()
    }

    fun onClickStartVideoChat() {
        appComponent.signalHandler
                .createRoom(emptyList(), listOf(userId))
                .map(Room::id)
                .observeOnMainThread()
                .doOnLoadingState(loading::set)
                .subscribeSimple(navigator::navigateToVideoChatPage)
                .bindToLifecycle()
    }

    interface Navigator {
        fun navigateToWalkieTalkiePage(roomId: String)
        fun navigateToVideoChatPage(roomId: String)
        fun navigateToDialPhone(phoneNumber: String)
    }
}