package com.xianzhitech.ptt.broker

import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.event.ConnectionErrorEvent
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.exception.ServerException
import com.xianzhitech.ptt.data.CurrentUser
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit


class SignalBroker(private val appComponent: AppComponent) {

    val isLoggedIn: Boolean
        get() = appComponent.signalApi.currentUser.value.isPresent

    init {
        appComponent.signalApi.currentUser
                .distinctUntilChanged { user -> user.orNull()?.id }
                .flatMapCompletable { user ->
                    if (user.isPresent) {
                        Observable.interval(0, 15, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                                .flatMapCompletable { syncContacts() }
                    } else {
                        Completable.complete()
                    }
                }
                .subscribe()
    }

    fun login(name: String, password: String): Completable {
        Preconditions.checkState(isLoggedIn.not())

        return appComponent.signalApi.events
                .filter { it is CurrentUser || it is LoginFailedEvent || it is ConnectionErrorEvent }
                .flatMapCompletable {
                    when (it) {
                        is CurrentUser -> Completable.complete()
                        is ConnectionErrorEvent -> Completable.error(it.err)
                        is LoginFailedEvent -> Completable.error(ServerException(it.name, it.message))
                        else -> throw IllegalStateException()
                    }
                }
                .doOnSubscribe { appComponent.signalApi.login(name, password) }
    }

    fun logout() {
        appComponent.signalHandler
    }

    fun syncContacts(): Completable {
        return appComponent.signalApi
                .syncContacts(appComponent.preference.contactVersion)
                .flatMapCompletable { (users, groups, version) ->
                    appComponent.storage
                            .replaceAllUsersAndGroups(users, groups)
                            .doOnComplete { appComponent.preference.contactVersion = version }
                }
    }
}