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
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.plusAssign
import com.xianzhitech.ptt.ext.sendLocalBroadcast
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.dto.JoinRoomResult
import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
import com.xianzhitech.ptt.service.impl.IOSignalService
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.base.BaseActivity
import rx.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.util.*
import java.util.concurrent.TimeUnit


class SignalServiceHandler(private val appContext: Context,
                           private val appComponent: AppComponent) {

    private val tokenProvider = TokenProviderImpl(appComponent.preference)
    private val loginStateSubject = BehaviorSubject.create(LoginState.EMPTY)
    private val roomStateSubject = BehaviorSubject.create(RoomState.EMPTY)

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var signalService: SignalService? = null
    private var signalServiceSubscription: Subscription? = null

    val loginState: Observable<LoginState> = loginStateSubject
    val roomState: Observable<RoomState> = roomStateSubject
    val loginStatus: Observable<LoginStatus> = loginState.map { it.status }.distinctUntilChanged()
    val roomStatus: Observable<RoomStatus> = roomState.map { it.status }.distinctUntilChanged()

    val currentUserId: String?
        get() = peekLoginState().currentUserID
    val currentRoomId: String?
        get() = peekRoomState().currentRoomId

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

    private fun doLogin() {
        loginStateSubject += peekLoginState().copy(LoginStatus.LOGIN_IN_PROGRESS, currentUserID = tokenProvider.authToken?.userId)

        val subscription = CompositeSubscription()
        subscription.add(appComponent.appService.retrieveAppParams()
                .subscribeOn(Schedulers.io())
                .toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    if (it.forceUpdate ?: false) {
                        throw ForceUpdateException(it)
                    }

                    if (subscription.isUnsubscribed) {
                        throw IllegalStateException("Login is unsubscribed unexpectedly")
                    }

                    val newSignalService = IOSignalService(it.signalServerEndpoint)
                    subscription.add(newSignalService.retrieveInvitation().subscribeSimple { onReceiveInvitation(it) })
                    subscription.add(newSignalService.retrieveRoomOnlineMemberUpdate().subscribeSimple { onRoomOnlineMemberUpdate(it) })
                    subscription.add(newSignalService.retrieveRoomSpeakerUpdate().subscribeSimple { onRoomSpeakerUpdate(it) })
                    subscription.add(newSignalService.retrieveUserKickedOutEvent().subscribeSimple { onUserKickedOut() })

                    signalService = newSignalService
                    newSignalService.login(tokenProvider)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : GlobalSubscriber<LoginResult>() {
                    override fun onNext(t: LoginResult) {
                        if (t.status == LoginStatus.IDLE) {
                            throw IllegalStateException("Signal service returned error status")
                        }

                        val roomState = peekRoomState()

                        when (t.status) {
                            LoginStatus.LOGGED_IN -> {
                                if (tokenProvider.authToken?.userId != t.user!!.id) {
                                    // Auto clear database
                                    appComponent.groupRepository.clear().execAsync().subscribeSimple()
                                    appComponent.roomRepository.clear().execAsync().subscribeSimple()
                                    appComponent.contactRepository.clear().execAsync().subscribeSimple()
                                }

                                val lastSyncDate = appComponent.preference.lastSyncContactTime
                                if (lastSyncDate == null || System.currentTimeMillis() - lastSyncDate.time >= Constants.SYNC_CONTACT_INTERVAL_MILLS) {
                                    subscription.add(signalService!!.retrieveContacts()
                                                .flatMap { appComponent.contactRepository.replaceAllContacts(it.users, it.groups).execAsync().toSingleDefault(Unit) }
                                                .subscribeSimple())
                                    appComponent.preference.lastSyncContactTime = Date()
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

                        loginStateSubject += peekLoginState().copy(status = t.status, currentUserID = t.token?.userId)
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

        signalServiceSubscription = subscription
    }

    fun logout() {
        mainThread {
            if (peekLoginState().status != LoginStatus.IDLE) {
                signalServiceSubscription?.unsubscribe()
                signalServiceSubscription = null
                signalService?.logout()?.subscribeSimple()
                signalService = null
                tokenProvider.authToken = null
                tokenProvider.loginName = null
                tokenProvider.loginPassword = null
                appComponent.preference.lastSyncContactTime = null

                loginStateSubject += LoginState.EMPTY
            }
        }
    }

    fun createRoom(request: CreateRoomRequest): Single<Room> {
        return Single.defer {
            ensureService().createRoom(request)
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun joinRoom(roomId: String): Completable {
        return Completable.create { subscriber ->
            val currentRoomId = peekRoomState().currentRoomId
            if (currentRoomId == roomId) {
                subscriber.onCompleted()
                return@create
            }

            val service = ensureService()

            if (currentRoomId != null) {
                service.leaveRoom(currentRoomId).subscribeSimple()
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
                        }

                        override fun onSuccess(value: JoinRoomResult) {
                            val state = peekRoomState()
                            if (state.currentRoomId == value.room.id && state.status == RoomStatus.JOINING) {
                                val newStatus = if (value.speakerId == null) {
                                    RoomStatus.JOINED
                                } else {
                                    RoomStatus.ACTIVE
                                }

                                roomStateSubject += state.copy(status = newStatus,
                                        onlineMemberIds = value.onlineMemberIds.toSet(),
                                        speakerId = value.speakerId,
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
                signalService.leaveRoom(roomId).subscribeSimple()

                roomStateSubject += RoomState.EMPTY
            }
        }
    }

    fun requestMic(): Single<Boolean> {
        return Single.defer<Boolean> {
            val roomState = peekRoomState()
            val currUserId = peekLoginState().currentUserID
            if (roomState.speakerId == currUserId) {
                return@defer Single.just(true)
            }

            if (roomState.status != RoomStatus.JOINED || roomState.currentRoomId == null || roomState.speakerId != null) {
                throw IllegalStateException("Can't request mic in room state $roomState")
            }

            roomStateSubject += roomState.copy(status = RoomStatus.REQUESTING_MIC)
            ensureService().requestMic(roomState.currentRoomId)
                    .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess {
                        if (it) {
                            val newRoomState = peekRoomState()
                            if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
                                    newRoomState.speakerId == null &&
                                    newRoomState.currentRoomId == roomState.currentRoomId &&
                                    peekLoginState().currentUserID == currUserId) {
                                roomStateSubject += newRoomState.copy(speakerId = currUserId, status = RoomStatus.ACTIVE)
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
            if (state.currentRoomId != null &&
                    state.speakerId == peekLoginState().currentUserID) {
                service.releaseMic(state.currentRoomId).subscribeSimple()
                roomStateSubject += state.copy(speakerId = null, status = if (state.status == RoomStatus.ACTIVE) RoomStatus.JOINED else state.status)
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
        appContext.startActivity(Intent(appContext, KickOutActivity::class.java))
    }

    private fun onRoomSpeakerUpdate(update: RoomSpeakerUpdate) {
        mainThread {
            val state = peekRoomState()
            if (state.status == RoomStatus.IDLE || state.currentRoomId != update.roomId) {
                return@mainThread
            }

            roomStateSubject += state.copy(speakerId = update.speakerId, status = if (update.speakerId == null) RoomStatus.JOINED else RoomStatus.ACTIVE)
        }
    }

    private fun onRoomOnlineMemberUpdate(update: RoomOnlineMemberUpdate) {
        mainThread {
            val state = peekRoomState()
            if (state.status == RoomStatus.IDLE || state.currentRoomId != update.roomId) {
                return@mainThread
            }

            roomStateSubject += state.copy(onlineMemberIds = update.memberIds.toSet())
        }
    }

    private fun onReceiveInvitation(invite: RoomInvitation) {
        appComponent.roomRepository.saveRooms(listOf((invite as ExtraRoomInvitation).room)).execAsync()
            .subscribeSimple {
                appContext.sendLocalBroadcast(Intent(ACTION_ROOM_INVITATION)
                        .putExtra(EXTRA_INVITATION, invite))
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
        const val ACTION_ROOM_INVITATION = "action_room_invitation"

        const val EXTRA_INVITATION = "extra_ri"

    }
}

class ForceUpdateException(val appParams: AppParams) : RuntimeException()

private class TokenProviderImpl(val preference: Preference) : TokenProvider {
    override var authToken: UserToken?
        get() = preference.userSessionToken
        set(value) {
            preference.userSessionToken = value
        }

    override var loginName: String? = null
    override var loginPassword: String? = null
}

