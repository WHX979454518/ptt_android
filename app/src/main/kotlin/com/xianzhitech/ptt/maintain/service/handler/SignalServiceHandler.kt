package com.xianzhitech.ptt.maintain.service.handler

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
import com.xianzhitech.ptt.maintain.service.*
import com.xianzhitech.ptt.maintain.service.dto.JoinRoomResult
import com.xianzhitech.ptt.maintain.service.dto.RoomOnlineMemberUpdate
import com.xianzhitech.ptt.maintain.service.dto.RoomSpeakerUpdate
import com.xianzhitech.ptt.model.Permission
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.KickOutActivity
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.slf4j.LoggerFactory
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subjects.SerializedSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger("SignalServiceHandler")


class SignalServiceHandler(private val appContext: Context,
                           private val appComponent: AppComponent) {

    private val loginStatusSubject = BehaviorSubject.create(LoginStatus.IDLE)
    private val roomStateSubject = BehaviorSubject.create(RoomState.EMPTY)

    val peekCurrentUserId: String?
        get() = appComponent.preference.userSessionToken?.userId

    val currentUserId : Observable<String?>
        get() = appComponent.preference.userSessionTokenSubject.map { it?.userId }.distinctUntilChanged()

    val loggedIn : Observable<Boolean>
        get() = appComponent.preference.userSessionTokenSubject.map { it != null }.distinctUntilChanged()

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var loginSubscription: Subscription? = null
    private var syncContactSubscription : Subscription? = null // Part of login subscription

    private var sendCommandSubject = PublishSubject<Command<*, *>>()
    private val authTokenFactory = AuthTokenFactoryImpl()
    private lateinit var coreService : CoreService

    val roomState: Observable<RoomState>
        get()  = roomStateSubject
    val roomStatus: Observable<RoomStatus>
        get() = roomState.map { peekRoomState().status }.distinctUntilChanged()
    val loginStatus: Observable<LoginStatus>
        get() = loginStatusSubject.distinctUntilChanged()

    val currentRoomId : Observable<String?>
        get() = roomState.map { it.currentRoomId }.distinctUntilChanged()

    var currentUserCache : User? = null
        private set

    init {
        appComponent.preference.userSessionToken?.let {
            authTokenFactory.set(it.userId, it.password)
            login()
        }
    }

    fun peekLoginStatus(): LoginStatus = loginStatusSubject.value
    fun peekCurrentRoomId() : String? = roomStateSubject.value.currentRoomId
    fun peekRoomState(): RoomState = roomStateSubject.value

    fun login() {
        mainThread {
            if (loginStatusSubject.value != LoginStatus.IDLE) {
                return@mainThread
            }

            doLogin()
        }
    }

    fun login(loginName: String, loginPassword: String) {
        mainThread {
            if (loginStatusSubject.value != LoginStatus.IDLE) {
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
        // Serialize login status to prevent unexpected result
        val loginStatusSubject = SerializedSubject(loginStatusSubject)

        loginStatusSubject += LoginStatus.LOGIN_IN_PROGRESS

        loginSubscription = getDeviceId().zipWith(retrieveAppParams(Constants.EMPTY_USER_ID), { first, second -> first to second })
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapObservable {
                    val (deviceId, appConfig) = it
                    if (appConfig.hasUpdate && appConfig.mandatory) {
                        throw ForceUpdateException(appConfig)
                    }

                    coreService = CoreServiceImpl(appComponent.httpClient, appConfig.signalServerEndpoint)

                    val androidReconnectPolicy = AndroidRetryPolicy(appContext)
                    receiveSignal(uri = Uri.parse(appConfig.signalServerEndpoint),
                            retryPolicy = object : RetryPolicy by androidReconnectPolicy {
                                override fun canContinue(err: Throwable): Boolean {
                                    return peekCurrentUserId != null
                                }
                            },
                            signalFactory = DefaultSignalFactory(),
                            loginStatusNotification = { loginStatusSubject += it },
                            commandEmitter = sendCommandSubject as Observable<Command<*, in Any>>,
                            deviceIdProvider = object : DeviceIdFactory {
                                override val deviceId: String
                                    get() = deviceId
                            },
                            connectivityProvider = object : ConnectivityProvider {
                                override val connected: Observable<Boolean>
                                    get() = appContext.getConnectivity(true)
                            },
                            loginTimeoutProvider = {
                                if (peekCurrentUserId == null) {
                                    // first time login, timeout is
                                    TimeUnit.MILLISECONDS.convert(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                }
                                else {
                                    40000L // <- Default socket timeout 30 sec
                                }
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
                        loginStatusSubject += LoginStatus.IDLE
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
            is UserLoggedInSignal -> onUserLoggedIn(signal.user)
        }
    }

    private fun onUserLoggedIn(user: UserObject) {
        currentUserCache = user
        val firstTimeLogin = appComponent.preference.userSessionToken?.userId != user.id
        appComponent.preference.userSessionToken = UserToken(user.id, authTokenFactory.password)

        // Save user to database
        appComponent.userRepository.saveUsers(listOf(user)).execAsync().subscribeSimple()

        if (firstTimeLogin) {
            logger.i { "Clearing room data for new user" }
            appComponent.roomRepository.clear().execAsync(true).await()
        }

        if (syncContactSubscription == null) {
            syncContactSubscription = loginStatusSubject
                    .distinctUntilChanged()
                    .filter { it == LoginStatus.LOGGED_IN }
                    .switchMap { Observable.interval(0, Constants.SYNC_CONTACT_INTERVAL_MILLS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()) }
                    .switchMap {
                        val version = appComponent.preference.contactVersion
                        logger.i { "Syncing contact version with localVersion=$version" }
                        coreService.syncContact(user.id, version).toObservable()
                    }
                    .switchMap {
                        logger.i { "Got contact $it" }
                        if (it != null) {
                            appComponent.contactRepository.replaceAllContacts(it.users, it.groups).execAsync().toSingleDefault(it).toObservable()
                        } else {
                            Observable.never<SyncContactResult>()
                        }
                    }
                    .onErrorResumeNext(Observable.never())
                    .subscribeSimple {
                        appComponent.preference.contactVersion = it.version
                    }
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
            val roomState = peekRoomState()
            if (roomState.currentRoomId != null) {
                quitRoom()
            }

            loginSubscription?.unsubscribe()
            loginSubscription = null
            syncContactSubscription?.unsubscribe()
            syncContactSubscription = null
            authTokenFactory.clear()
            appComponent.preference.apply {
                userSessionToken = null
                lastExpPromptTime = null
                contactVersion = -1
            }

            loginStatusSubject += LoginStatus.IDLE
            currentUserCache = null
        }
    }

    fun createRoom(groupIds : Iterable<String> = emptyList(), userIds : Iterable<String> = emptyList()): Single<Room> {
        return Single.defer {
            ensureLoggedIn()

            val permissions = currentUserCache!!.permissions
            if ((userIds.count() == 1 && permissions.contains(Permission.MAKE_INDIVIDUAL_CALL).not()) ||
                    (groupIds.sizeAtLeast(1) && permissions.contains(Permission.MAKE_GROUP_CALL).not())) {
                throw StaticUserException(R.string.error_no_permission)
            }

            CreateRoomCommand(groupIds = groupIds, extraMemberIds = userIds)
                    .send()
                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }

        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun waitForUserLogin() : Completable {
        return Completable.defer {
            when (loginStatusSubject.value) {
                LoginStatus.LOGGED_IN -> return@defer Completable.complete()
                LoginStatus.IDLE -> return@defer Completable.error(StaticUserException(R.string.error_unable_to_connect))
                else -> {}
            }

            logger.i { "Waiting for user to log in..." }
            appComponent.signalHandler.loginStatus
                    .first { it == LoginStatus.LOGGED_IN }
                    .toCompletable()
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun joinRoom(roomId: String, fromInvitation: Boolean): Completable {
        return waitForUserLogin()
                .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .andThen(Completable.create { subscriber : CompletableSubscriber ->
                    val currRoomState = peekRoomState()
                    val currentRoomId = currRoomState.currentRoomId
                    if (currentRoomId == roomId && currRoomState.status != RoomStatus.IDLE) {
                        subscriber.onCompleted()
                        return@create
                    }

                    if (currentRoomId != null) {
                        LeaveRoomCommand(currentRoomId, appComponent.preference.keepSession.not()).send()
                    }

                    roomStateSubject += RoomState.EMPTY.copy(status = RoomStatus.JOINING, currentRoomId = roomId)

                    JoinRoomCommand(roomId, fromInvitation)
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

                }.subscribeOn(AndroidSchedulers.mainThread()))
    }

    fun quitRoom() {
        mainThread {
            if (loginStatusSubject.value == LoginStatus.LOGGED_IN) {
                val roomId = peekRoomState().currentRoomId
                if (roomId != null) {
                    LeaveRoomCommand(roomId, appComponent.preference.keepSession.not()).send()
                }
            }

            roomStateSubject += RoomState.EMPTY
        }
    }

    fun requestMic(): Single<Boolean> {
        return Single.defer<Boolean> {
            ensureLoggedIn()

            val roomState = peekRoomState()
            val userId = peekCurrentUserId
            if (roomState.speakerId == userId) {
                return@defer Single.just(true)
            }

            if (roomState.canRequestMic(currentUserCache!!).not()) {
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
                                    peekCurrentUserId == userId) {
                                roomStateSubject += newRoomState.copy(
                                        speakerId = userId,
                                        speakerPriority = currentUserCache!!.priority,
                                        status = RoomStatus.ACTIVE)
                            }
                        }
                    }
                    .doOnError {
                        val newRoomState = peekRoomState()
                        if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
                                newRoomState.currentRoomId == roomState.currentRoomId &&
                                peekCurrentUserId == userId) {
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
                    (state.speakerId == peekCurrentUserId || state.status == RoomStatus.REQUESTING_MIC)) {
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
            val currUserId = peekCurrentUserId!!
            val oldPassword = oldPassword.toMD5()
            val newPassword = newPassword.toMD5()
            ChangePasswordCommand(currUserId, oldPassword, newPassword)
                    .send()
                    .doOnSuccess {
                        mainThread {
                            if (peekCurrentUserId == currUserId) {
                                authTokenFactory.setPassword(newPassword)
                                appComponent.preference.userSessionToken = UserToken(currUserId, newPassword)
                            }
                        }
                    }
                    .toCompletable()
        }
    }

    private fun ensureLoggedIn() {
        if (loginStatusSubject.value != LoginStatus.LOGGED_IN || currentUserCache == null) {
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
            if (roomId == peekCurrentRoomId()) {
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
        appContext.sendBroadcast(Intent(ACTION_ROOM_INVITATION).putExtra(EXTRA_INVITATION, invite))
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
