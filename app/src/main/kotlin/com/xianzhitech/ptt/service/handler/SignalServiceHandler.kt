package com.xianzhitech.ptt.service.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.dto.JoinRoomResult
import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.slf4j.LoggerFactory
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger("SignalServiceHandler")


class SignalServiceHandler(private val appContext: Context,
                           private val appComponent: AppComponent) {

    private val loginStateSubject = BehaviorSubject.create(LoginState.EMPTY)
    private val roomStateSubject = BehaviorSubject.create(RoomState.EMPTY)

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var loginSubscription: Subscription? = null
    private var syncContactSubscription : Subscription? = null // Part of login subscription

    private var sendCommandSubject = PublishSubject<Command<*, *>>()
    private val authTokenFactory = AuthTokenFactoryImpl()

    val loginState: Observable<LoginState> = loginStateSubject
    val roomState: Observable<RoomState> = roomStateSubject
    val loginStatus: Observable<LoginStatus>
        get() = loginState.map { peekLoginState().status }.distinctUntilChanged()
    val roomStatus: Observable<RoomStatus>
        get() = roomState.map { peekRoomState().status }.distinctUntilChanged()

    val currentUserId: String?
        get() = peekLoginState().currentUser?.id
    val currentRoomId: String?
        get() = peekRoomState().currentRoomId

    val currentUserIdSubject : Observable<String?>
        get() = loginState.map { currentUserId }.distinctUntilChanged()

    val currentRoomIdSubject : Observable<String?>
        get() = roomState.map { currentRoomId }.distinctUntilChanged()

    init {
        appComponent.preference.userSessionToken?.let {
            authTokenFactory.set(it.userId, it.password)
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

            authTokenFactory.set(loginName, loginPassword.toMD5())
            doLogin()
        }
    }

    private fun getDeviceId() : Single<String> {
        return appComponent.preference.deviceId?.let { Single.just(it) }
                ?: appComponent.appService.registerDevice(AppInfo(appContext)).doOnSuccess { appComponent.preference.deviceId = it }

    }

    private fun doLogin() {
        loginSubscription = getDeviceId().zipWith(retrieveAppParams(Constants.EMPTY_USER_ID), { first, second -> first to second })
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapObservable {
                    val (deviceId, appConfig) = it
                    if (appConfig.hasUpdate && appConfig.mandatory) {
                        throw ForceUpdateException(appConfig)
                    }

                    receiveSignal(uri = Uri.parse(appConfig.signalServerEndpoint),
                            retryPolicy = AndroidReconnectPolicy(appContext),
                            signalFactory = DefaultSignalFactory(),
                            loginStateNotification = {
                                if (it.status == LoginStatus.LOGGED_IN) {
                                    appComponent.preference.userSessionToken = UserToken(it.currentUserID!!, authTokenFactory.password)
                                    appComponent.userRepository.saveUsers(listOf(it.currentUser!!)).execAsync().subscribeSimple()
                                }

                                loginStateSubject.onNext(it)
                            },
                            commandEmitter = sendCommandSubject as Observable<Command<*, in Any>>,
                            deviceIdProvider = object : DeviceIdFactory {
                                override val deviceId: String
                                    get() = deviceId
                            },
                            connectivityProvider = object : ConnectivityProvider {
                                override val connected: Observable<Boolean>
                                    get() = appContext.getConnectivity(true)
                            },
                            authTokenFactory = authTokenFactory)
                }
                .observeOnMainThread()
                .subscribe(object : Subscriber<Signal>() {
                    override fun onNext(t: Signal) {
                        dispatchSignal(t)
                    }

                    override fun onCompleted() { }

                    override fun onError(e: Throwable?) {
                        loginStateSubject.onNext(LoginState.EMPTY)
                        handleSignalError(e)
                    }
                })
    }

    private fun handleSignalError(err: Throwable?) {
        showToast(err.describeInHumanMessage(appContext))
    }

    private fun dispatchSignal(signal: Signal) {
        when (signal) {
            is RoomUpdateSignal -> onRoomUpdate(signal.room)
            is UserKickOutSignal -> onUserKickOut()
            is RoomKickOutSignal -> onRoomKickedOut(signal.roomId)
            is RoomSpeakerUpdateSignal -> onRoomSpeakerUpdate(signal.update)
            is RoomOnlineMemberUpdateSignal -> onRoomOnlineMemberUpdate(signal.update)
            is RoomInviteSignal -> onReceiveInvitation(signal.invitation)
        }
    }

    private fun onRoomUpdate(newRoom: Room) {
        appComponent.roomRepository.saveRooms(listOf(newRoom)).execAsync().subscribeSimple()
    }

    private fun retrieveAppParams(loginName: String): Single<AppConfig> {
        return appComponent.appService.retrieveAppConfig(loginName)
                .subscribeOn(Schedulers.io())
                .doOnSuccess {
                    // 将结果缓存起来, 以便无网络时使用
                    appComponent.preference.lastAppParams = it
                }
                .onErrorResumeNext {
                    // 出错了: 如果我们之前存有AppParam, 则使用那个, 否则就继续返回错误
                    appComponent.preference.lastAppParams?.let { Single.just(it) } ?: Single.error(it)
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
                authTokenFactory.clear()
                appComponent.preference.userSessionToken = null
                appComponent.preference.lastExpPromptTime = null

                loginStateSubject += LoginState.EMPTY
            }
        }
    }

    fun createRoom(groupIds : Iterable<String> = emptyList(), userIds : Iterable<String> = emptyList()): Single<Room> {
        return Single.defer {
            ensureLoggedIn()

            CreateRoomCommand(groupIds = groupIds, extraMemberIds = userIds)
                    .send()
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }

        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun joinRoom(roomId: String): Completable {
        return Completable.create { subscriber ->
            ensureLoggedIn()

            val currRoomState = peekRoomState()
            val currentRoomId = currRoomState.currentRoomId
            if (currentRoomId == roomId && currRoomState.status != RoomStatus.OFFLINE) {
                subscriber.onCompleted()
                return@create
            }

            if (currentRoomId != null) {
                LeaveRoomCommand(currentRoomId, appComponent.preference.keepSession.not()).send()
            }

            roomStateSubject += RoomState.EMPTY.copy(status = RoomStatus.JOINING, currentRoomId = roomId)

            JoinRoomCommand(roomId)
                    .send()
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
                            logger.d { "Join room result $value" }

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
            ensureLoggedIn()

            val roomId = peekRoomState().currentRoomId
            if (roomId != null) {
                LeaveRoomCommand(roomId, appComponent.preference.keepSession.not()).send()
                roomStateSubject += RoomState.EMPTY
            }
        }
    }

    fun requestMic(): Single<Boolean> {
        return Single.defer<Boolean> {
            ensureLoggedIn()

            val roomState = peekRoomState()
            val loginState = peekLoginState()
            val currUserId = loginState.currentUser?.id
            if (roomState.speakerId == currUserId) {
                return@defer Single.just(true)
            }

            if (roomState.canRequestMic(loginState.currentUser).not()) {
                throw IllegalStateException("Can't request mic in room state $roomState")
            }

            logger.d { "Requesting mic... $roomState" }

            roomStateSubject += roomState.copy(status = RoomStatus.REQUESTING_MIC)
            RequestMicCommand(roomState.currentRoomId!!)
                    .send()
                    .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess {
                        if (it) {
                            val newRoomState = peekRoomState()
                            logger.d { "Successfully requested mic in $newRoomState" }
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
            ensureLoggedIn()
            val state = peekRoomState()
            logger.d { "Releasing mic in $state" }

            if (state.currentRoomId != null &&
                    (state.speakerId == peekLoginState().currentUserID || state.status == RoomStatus.REQUESTING_MIC)) {
                ReleaseMicCommand(state.currentRoomId).send()
                roomStateSubject += state.copy(speakerId = null, speakerPriority = null, status = RoomStatus.JOINED)
            }
        }
    }

    fun retrieveRoomInfo(roomId: String): Single<Room> {
        //TODO:
        return Single.create {  }
    }

    fun updateRoomMembers(roomId: String, roomMemberIds: Collection<String>): Completable {
        return Completable.defer {
            ensureLoggedIn()

            AddRoomMembersCommand(roomId, roomMemberIds)
                    .send()
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }
                    .toCompletable()
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun changePassword(oldPassword: String, newPassword: String): Completable {
        return Completable.defer {
            ensureLoggedIn()
            val currUserId = peekLoginState().currentUserID!!
            val oldPassword = oldPassword.toMD5()
            val newPassword = newPassword.toMD5()
            ChangePasswordCommand(currUserId, oldPassword, newPassword)
                    .send()
                    .doOnSuccess {
                        mainThread {
                            if (peekLoginState().currentUserID == currUserId) {
                                authTokenFactory.setPassword(newPassword)
                                appComponent.preference.userSessionToken = UserToken(currUserId, newPassword)
                            }
                        }
                    }
                    .toCompletable()
        }
    }

    private fun ensureLoggedIn() {
        if (peekLoginState().status != LoginStatus.LOGGED_IN) {
            throw StaticUserException(R.string.error_unable_to_connect)
        }
    }

    private fun onUserKickOut() {
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

            logger.d { "Online member IDs updated to ${update.memberIds}, curr state = $state" }

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

    private fun <R, C : Command<R, *>> C.send() : Single<R> {
        sendCommandSubject.onNext(this)
        return getAsync()
    }

    companion object {
        const val ACTION_ROOM_INVITATION = "SignalService.Room"

        const val EXTRA_INVITATION = "extra_ri"
    }
}



class ForceUpdateException(val appParams: AppConfig) : RuntimeException()


private class AuthTokenFactoryImpl() : AuthTokenFactory {
    private val auth = AtomicReference<Pair<String, String>>()
    val password : String
        get() = auth.get().second

    override val authToken: String
        get() {
            val (name, pass) = auth.get() ?: throw NullPointerException()
            val token = "$name:$pass${name.guessLoginPostfix()}"
            return "Basic ${token.toBase64()}"
        }

    fun set(loginName : String, password : String) {
        auth.set(loginName to password)
    }

    fun setPassword(newPassword: String) : Pair<String, String> {
        val (name, oldPass) = auth.get() ?: throw NullPointerException()
        val result = name to newPassword
        auth.set(result)
        return result
    }

    fun clear() {
        auth.set(null)
    }

    private fun String.guessLoginPostfix() : String {
        return when {
            matches(PHONE_MATCHER) -> ":PHONE"
            matches(EMAIL_MATCHER) -> ":MAIL"
            else -> ""
        }
    }

    companion object {
        private val PHONE_MATCHER = Regex("^1[2-9]\\d{9}$")
        private val EMAIL_MATCHER = Regex(".+@.+\\..+$")
    }
}
