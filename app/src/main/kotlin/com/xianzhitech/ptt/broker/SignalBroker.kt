package com.xianzhitech.ptt.broker

import android.content.Context
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.event.ConnectionErrorEvent
import com.xianzhitech.ptt.api.event.LoginFailedEvent
import com.xianzhitech.ptt.api.exception.ServerException
import com.xianzhitech.ptt.ext.logErrorAndForget
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit


class SignalBroker(private val appComponent: AppComponent,
                   appContext: Context) {

    private val signalApi = SignalApi(appComponent, appContext)

    val events = signalApi.events
    val connectionState = signalApi.connectionState
    val currentUser = signalApi.currentUser

    val currentVideoRoom : BehaviorSubject<Optional<String>> = BehaviorSubject.createDefault(Optional.absent<String>())
    val currentWalkieTalkieRoom : BehaviorSubject<Optional<String>> = BehaviorSubject.createDefault(Optional.absent<String>())

    val isLoggedIn: Boolean
        get() = signalApi.currentUser.value.isPresent

    init {
        signalApi.connectionState
                .distinctUntilChanged()
                .switchMap { state ->
                    if (state == SignalApi.ConnectionState.CONNECTED) {
                        Observable.interval(0, 15, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                                .switchMap { syncContacts().logErrorAndForget().toObservable<Unit>() }
                    } else {
                        Observable.empty()
                    }
                }
                .subscribe()
    }

    fun login(name: String, password: String): Completable {
        Preconditions.checkState(isLoggedIn.not())

        @Suppress("UNCHECKED_CAST")
        return Completable.fromObservable((signalApi.events as Observable<Any>)
                .mergeWith(signalApi.connectionState)
                .filter { it == SignalApi.ConnectionState.CONNECTED || it is ConnectionErrorEvent || it is LoginFailedEvent }
                .firstOrError()
                .flatMapObservable {
                    when (it) {
                        SignalApi.ConnectionState.CONNECTED -> Observable.empty<Unit>()
                        is ConnectionErrorEvent -> Observable.error(it.err)
                        is LoginFailedEvent -> Observable.error(ServerException(it.name, it.message))
                        else -> throw IllegalStateException()
                    }
                })
                .doOnSubscribe { signalApi.login(name, password) }

    }

    fun logout() {
        appComponent.storage.clearUsersAndGroups()
        signalApi.logout()
    }

    fun syncContacts(): Completable {
        return signalApi
                .syncContacts(appComponent.preference.contactVersion)
                .flatMapCompletable { contact ->
                    appComponent.storage
                            .replaceAllUsersAndGroups(contact.members, contact.groups)
                            .doOnComplete { appComponent.preference.contactVersion = contact.version }
                }
    }
}