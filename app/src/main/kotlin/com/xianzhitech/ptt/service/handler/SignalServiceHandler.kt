package com.xianzhitech.ptt.service.handler

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.dto.JoinRoomResult
import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
import com.xianzhitech.ptt.service.impl.IOSignalService
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.util.concurrent.TimeUnit


class SignalServiceHandler(private val appContext: Context,
                           private val appComponent: AppComponent) {

    private val tokenProvider = TokenProviderImpl(appComponent.preference)
    private val loginStateSubject = BehaviorSubject.create(LoginState.EMPTY)
    private val roomStateSubject = BehaviorSubject.create(RoomState.EMPTY)

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var signalService: SignalService? = null
    private var loginSubscription: CompositeSubscription? = null
    private var syncContactSubscription : Subscription? = null // Part of login subscription

    val loginState: Observable<LoginState> = loginStateSubject
    val roomState: Observable<RoomState> = roomStateSubject
    val loginStatus: Observable<LoginStatus>
        get() = loginState.map { peekLoginState().status }.distinctUntilChanged()
    val roomStatus: Observable<RoomStatus>
        get() = roomState.map { peekRoomState().status }.distinctUntilChanged()

    val currentUserId: String?
        get() = peekLoginState().currentUserID
    val currentRoomId: String?
        get() = peekRoomState().currentRoomId

    val currentUserIdSubject : Observable<String?>
        get() = loginState.map { currentUserId }.distinctUntilChanged()

    val currentRoomIdSubject : Observable<String?>
        get() = roomState.map { currentRoomId }.distinctUntilChanged()

    init {
        // Auto login if we have auth token
        tokenProvider.authToken?.let {
            login()
        }
    }

    fun peekLoginState(): LoginState = loginStateSubject.value
    fun peekRoomState(): RoomState = roomStateSubject.value

    fun login() {
        mainThread {
            val state = loginStateSubject.value
            if (state.status != LoginStatus.IDLE) {
                return@mainThread
            }

            doLogin()
        }
    }

    fun login(loginName: String, loginPassword: String) {
        mainThread {
            val state = loginStateSubject.value
            if (state.status != LoginStatus.IDLE) {
                return@mainThread
            }

            tokenProvider.authToken = null
            tokenProvider.loginName = loginName
            tokenProvider.loginPassword = loginPassword

            doLogin()
        }
    }

    private fun getDeviceId() : Observable<String> {
        return appComponent.preference.deviceId?.toObservable()
                ?: appComponent.appService.registerDevice(AppInfo(appContext)).doOnSuccess { appComponent.preference.deviceId = it }.toObservable()

    }

    private fun doLogin() {
        loginStateSubject += peekLoginState().copy(LoginStatus.LOGIN_IN_PROGRESS, currentUserID = tokenProvider.authToken?.userId)

        val subscription = CompositeSubscription()
        subscription.add(
                getDeviceId().combineWith(retrieveAppParams(tokenProvider.loginName ?: Constants.EMPTY_USER_ID))
                        .observeOn(AndroidSchedulers.mainThread())
                        .flatMap {
                            if (it.second.hasUpdate && it.second.mandatory) {
                                throw ForceUpdateException(it.second)
                            }

                            if (subscription.isUnsubscribed) {
                                throw IllegalStateException("Login is unsubscribed unexpectedly")
                            }

                            val newSignalService = IOSignalService(it.second.signalServerEndpoint, it.first, appComponent.httpClient)
                            subscription.add(newSignalService.retrieveInvitation().subscribeSimple { onReceiveInvitation(it) })
                            subscription.add(newSignalService.retrieveRoomOnlineMemberUpdate().subscribeSimple { onRoomOnlineMemberUpdate(it) })
                            subscription.add(newSignalService.retrieveRoomInfoUpdate().subscribeSimple { onRoomUpdate(it) })
                            subscription.add(newSignalService.retrieveRoomSpeakerUpdate().subscribeSimple { onRoomSpeakerUpdate(it) })
                            subscription.add(newSignalService.retrieveUserKickedOutEvent().subscribeSimple { onUserKickedOut() })
                            subscription.add(newSignalService.retrieveRoomKickedOutEvent().subscribeSimple { onRoomKickedOut(it) })

                            signalService = newSignalService
                            newSignalService.login(tokenProvider)
                        }
                        .timeout {
                            if (it.status == LoginStatus.LOGIN_IN_PROGRESS && it.token == null) {
                                Observable.timer(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                            } else {
                                Observable.never()
                            }
                        }
                        .switchMap { result ->
                            if (result.status == LoginStatus.LOGGED_IN && tokenProvider.authToken?.userId != result.user!!.id) {
                                appComponent.preference.contactVersion = Constants.INVALID_CONTACT_VERSION

                                // Auto clear database
                                Completable.concat(
                                        appComponent.groupRepository.clear().execAsync(),
                                        appComponent.roomRepository.clear().execAsync(),
                                        appComponent.contactRepository.clear().execAsync())
                                        .andThen(result.toObservable())
                            } else {
                                result.toObservable()
                            }
                        }
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<LoginResult>() {
                            override fun onNext(t: LoginResult) {
                                if (t.status == LoginStatus.IDLE) {
                                    throw IllegalStateException("Signal service returned error status")
                                }

                                val roomState = peekRoomState()

                                when (t.status) {
                                    LoginStatus.LOGGED_IN -> {
                                        if (syncContactSubscription == null || syncContactSubscription!!.isUnsubscribed) {
                                            syncContactSubscription = Observable.interval(0L, Constants.SYNC_CONTACT_INTERVAL_MILLS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                                                    .switchMap { signalService!!.syncContacts(appComponent.preference.contactVersion, t.user!!.id).toObservable() }
                                                    .retry(5) // 重试5次
                                                    .onErrorResumeNext(Observable.just(null))
                                                    .observeOnMainThread()
                                                    .subscribeSimple {
                                                        if (it != null) {
                                                            appComponent.contactRepository.replaceAllContacts(it.users, it.groups).execAsync().subscribeSimple()
                                                            appComponent.preference.contactVersion = it.version
                                                        }
                                                    }
                                            subscription.add(syncContactSubscription)
                                        }

                                        appComponent.userRepository.saveUsers(listOf(t.user!!)).execAsync().subscribeSimple()

                                        tokenProvider.authToken = t.token
                                        if (roomState.status == RoomStatus.OFFLINE && roomState.currentRoomId != null) {
                                            // Auto reconnect to room
                                            joinRoom(roomState.currentRoomId).subscribeSimple()
                                        }
                                    }

                                    LoginStatus.IDLE -> {
                                        if (roomState.currentRoomId != null) {
                                            roomStateSubject += roomState.copy(status = RoomStatus.OFFLINE, speakerId = null, onlineMemberIds = emptySet())
                                        }
                                    }

                                    else -> {
                                    }
                                }

                                loginStateSubject += peekLoginState().copy(status = t.status, currentUserID = t.token?.userId, currentUser = t.user)
                                if (roomState.status.inRoom && t.status == LoginStatus.OFFLINE) {
                                    roomStateSubject += roomState.copy(status = RoomStatus.OFFLINE, onlineMemberIds = emptySet())
                                }
                            }

                            override fun onCompleted() {
                            }

                            override fun onError(error: Throwable) {
                                super.onError(error)
                                logout()

                                (appComponent.activityProvider.currentStartedActivity as? BaseActivity)?.let {
                                    it.onLoginError(error)
                                } ?: showToast(error.describeInHumanMessage(appContext))
                            }
                        })
        )

        loginSubscription = subscription
    }

    private fun onRoomUpdate(newRoom: Room) {
        appComponent.roomRepository.saveRooms(listOf(newRoom)).execAsync().subscribeSimple()
    }

    private fun retrieveAppParams(loginName: String): Observable<AppConfig> {
        return appComponent.appService.retrieveAppConfig(loginName)
                .subscribeOn(Schedulers.io())
                .toObservable()
                .doOnNext {
                    // 将结果缓存起来, 以便无网络时使用
                    appComponent.preference.lastAppParams = it
                }
                .onErrorResumeNext {
                    // 出错了: 如果我们之前存有AppParam, 则使用那个, 否则就继续返回错误
                    appComponent.preference.lastAppParams?.toObservable() ?: Observable.error(it)
                }
    }

    fun logout() {
        mainThread {
            val loginState = peekLoginState()
            val roomState = peekRoomState()
            if (roomState.currentRoomId != null) {
                quitRoom()
            }

            if (loginState.status != LoginStatus.IDLE) {
                loginSubscription?.unsubscribe()
                loginSubscription = null
                signalService?.logout()?.subscribeSimple()
                signalService = null
                tokenProvider.authToken = null
                tokenProvider.loginName = null
                tokenProvider.loginPassword = null
                appComponent.preference.lastExpPromptTime = null

                loginStateSubject += LoginState.EMPTY
            }
        }
    }

    fun createRoom(request: CreateRoomRequest): Single<Room> {
        return Single.defer {
            // Check permission first
            ensureService().createRoom(request)
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun joinRoom(roomId: String): Completable {
        return Completable.create { subscriber ->
            val currRoomState = peekRoomState()
            val currentRoomId = currRoomState.currentRoomId
            if (currentRoomId == roomId && currRoomState.status != RoomStatus.OFFLINE) {
                subscriber.onCompleted()
                return@create
            }

            val service: SignalService
            try {
                service = ensureService()
            } catch(e: Exception) {
                subscriber.onError(e)
                return@create
            }

            if (currentRoomId != null) {
                service.leaveRoom(currentRoomId, appComponent.preference.keepSession.not()).subscribeSimple()
            }

            roomStateSubject += RoomState.EMPTY.copy(status = RoomStatus.JOINING, currentRoomId = roomId)

            service.joinRoom(roomId)
                    .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it.room)).execAsync().toSingleDefault(it) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : SingleSubscriber<JoinRoomResult>() {
                        override fun onError(error: Throwable) {
                            subscriber.onError(error)

                            if (peekRoomState().currentRoomId == roomId) {
                                quitRoom()
                            }

                            if (error is KnownServerException &&
                                    error.errorName == "room_not_exists") {
                                appComponent.roomRepository.removeRooms(listOf(roomId)).execAsync().subscribeSimple()
                            }
                        }

                        override fun onSuccess(value: JoinRoomResult) {
                            logtagd("SignalServiceHandler", "Join room result $value")

                            val state = peekRoomState()
                            if (state.currentRoomId == value.room.id && state.status == RoomStatus.JOINING) {
                                val newStatus = if (value.speakerId == null) {
                                    RoomStatus.JOINED
                                } else {
                                    RoomStatus.ACTIVE
                                }

                                roomStateSubject += state.copy(status = newStatus,
                                        onlineMemberIds = state.onlineMemberIds + value.onlineMemberIds.toSet(),
                                        currentRoomInitiatorUserId = value.initiatorUserId,
                                        speakerId = value.speakerId,
                                        speakerPriority = value.speakerPriority,
                                        voiceServer = value.voiceServerConfiguration)
                            }

                            subscriber.onCompleted()
                        }
                    })

        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun quitRoom() {
        mainThread {
            val signalService = signalService ?: return@mainThread
            val roomId = peekRoomState().currentRoomId
            if (roomId != null) {
                signalService.leaveRoom(roomId, appComponent.preference.keepSession.not()).subscribeSimple()

                roomStateSubject += RoomState.EMPTY
            }
        }
    }

    fun requestMic(): Single<Boolean> {
        return Single.defer<Boolean> {
            val roomState = peekRoomState()
            val loginState = peekLoginState()
            val currUserId = loginState.currentUserID
            if (roomState.speakerId == currUserId) {
                return@defer Single.just(true)
            }

            if (roomState.canRequestMic(loginState.currentUser).not()) {
                throw IllegalStateException("Can't request mic in room state $roomState")
            }

            logtagd("SignalHandler", "Requesting mic... %s", roomState)

            roomStateSubject += roomState.copy(status = RoomStatus.REQUESTING_MIC)
            ensureService().requestMic(roomState.currentRoomId!!)
                    .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess {
                        if (it) {
                            val newRoomState = peekRoomState()
                            logtagd("SignalHandler", "Successfully requested mic in %s", newRoomState)
                            if ((newRoomState.status == RoomStatus.REQUESTING_MIC || newRoomState.status == RoomStatus.ACTIVE) &&
                                    newRoomState.currentRoomId == roomState.currentRoomId &&
                                    peekLoginState().currentUserID == currUserId) {
                                roomStateSubject += newRoomState.copy(
                                        speakerId = currUserId,
                                        speakerPriority = loginState.currentUser?.priority,
                                        status = RoomStatus.ACTIVE)
                            }
                        }
                    }
                    .doOnError {
                        val newRoomState = peekRoomState()
                        if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
                                newRoomState.currentRoomId == roomState.currentRoomId &&
                                peekLoginState().currentUserID == currUserId) {
                            val newStatus = if (newRoomState.speakerId == null) {
                                RoomStatus.JOINED
                            } else {
                                RoomStatus.ACTIVE
                            }
                            roomStateSubject += newRoomState.copy(status = newStatus)
                        }
                    }
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun releaseMic() {
        mainThread {
            val service = signalService ?: return@mainThread
            val state = peekRoomState()
            logtagd("SignalHandler", "Releasing mic in %s", state)

            if (state.currentRoomId != null &&
                    (state.speakerId == peekLoginState().currentUserID || state.status == RoomStatus.REQUESTING_MIC)) {
                service.releaseMic(state.currentRoomId).subscribeSimple()
                roomStateSubject += state.copy(speakerId = null, speakerPriority = null, status = RoomStatus.JOINED)
            }
        }
    }

    fun retrieveRoomInfo(roomId: String): Single<Room> {
        return Single.defer {
            ensureService().retrieveRoomInfo(roomId)
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun updateRoomMembers(roomId: String, roomMemberIds: Collection<String>): Completable {
        return Completable.defer {
            Completable.fromSingle(ensureService().updateRoomMembers(roomId, roomMemberIds)
                    .doOnSuccess { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().subscribeSimple() })
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun changePassword(oldPassword: String, newPassword: String): Completable {
        return Completable.defer {
            val currUserId = peekLoginState().currentUserID
            Completable.fromSingle(ensureService().changePassword(tokenProvider, oldPassword, newPassword)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess {
                        if (peekLoginState().currentUserID == currUserId) {
                            tokenProvider.authToken = it
                        }
                    })
        }
    }

    private fun ensureService(): SignalService {
        val ret = signalService ?: throw IllegalStateException("User not logged in")

        if (peekLoginState().status != LoginStatus.LOGGED_IN) {
            throw StaticUserException(R.string.error_unable_to_connect)
        }

        return ret
    }

    private fun onUserKickedOut() {
        mainThread {
            logout()
            val intent = Intent(appContext, KickOutActivity::class.java)
            appComponent.activityProvider.currentStartedActivity?.startActivity(intent)
                    ?: appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private fun onRoomKickedOut(roomId : String) {
        mainThread {
            if (roomId == currentRoomId) {
                quitRoom()
                Toast.makeText(appContext, R.string.room_kicked_out, Toast.LENGTH_LONG).show()
                (appComponent.activityProvider.currentStartedActivity as? RoomActivity)?.finish()
            }
        }
    }

    private fun onRoomSpeakerUpdate(update: RoomSpeakerUpdate) {
        mainThread {
            val state = peekRoomState()
            if (state.status == RoomStatus.IDLE || state.currentRoomId != update.roomId) {
                return@mainThread
            }

            roomStateSubject += state.copy(
                    speakerId = update.speakerId,
                    speakerPriority = update.speakerPriority,
                    status = if (update.speakerId == null) RoomStatus.JOINED else RoomStatus.ACTIVE)
        }
    }

    private fun onRoomOnlineMemberUpdate(update: RoomOnlineMemberUpdate) {
        mainThread {
            val state = peekRoomState()
            if (state.status == RoomStatus.IDLE ||
                    state.currentRoomId != update.roomId) {
                return@mainThread
            }

            logd("Online member IDs updated to ${update.memberIds}, curr state = $state")

            roomStateSubject += state.copy(onlineMemberIds = update.memberIds.toSet())
        }
    }

    private fun onReceiveInvitation(invite: RoomInvitation) {
        appComponent.roomRepository.saveRooms(listOf((invite as ExtraRoomInvitation).room)).execAsync()
                .subscribeSimple {
                    appContext.sendBroadcast(Intent(ACTION_ROOM_INVITATION).putExtra(EXTRA_INVITATION, invite))
                }
    }

    private fun showToast(message: CharSequence) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }

    private fun mainThread(runnable: () -> Unit) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            runnable()
        } else {
            mainThreadHandler.post(runnable)
        }
    }

    companion object {
        const val ACTION_ROOM_INVITATION = "SignalService.Room"

        const val EXTRA_INVITATION = "extra_ri"
    }
}

class ForceUpdateException(val appParams: AppConfig) : RuntimeException()

private class TokenProviderImpl(val preference: Preference) : TokenProvider {
    override var authToken: UserToken?
        get() = preference.userSessionToken
        set(value) {
            preference.userSessionToken = value
        }

    override var loginName: String? = null
    override var loginPassword: String? = null
}

