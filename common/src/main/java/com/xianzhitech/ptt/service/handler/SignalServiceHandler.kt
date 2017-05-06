//package com.xianzhitech.ptt.service.handler
//
//import android.content.Context
//import android.content.Intent
//import android.os.Handler
//import android.os.Looper
//import android.widget.Toast
//import com.baidu.location.LocationClient
//import com.baidu.mapapi.model.LatLngBounds
//import com.crashlytics.android.answers.Answers
//import com.crashlytics.android.answers.CustomEvent
//import com.crashlytics.android.answers.LoginEvent
//import com.xianzhitech.ptt.AppComponent
//import com.xianzhitech.ptt.Constants
//import com.xianzhitech.ptt.R
//import com.xianzhitech.ptt.api.dto.AppConfig
//import com.xianzhitech.ptt.ext.*
//import com.xianzhitech.ptt.model.Location
//import com.xianzhitech.ptt.model.Message
//import com.xianzhitech.ptt.model.Permission
//import com.xianzhitech.ptt.model.Room
//import com.xianzhitech.ptt.service.*
//import com.xianzhitech.ptt.service.dto.JoinRoomResult
//import com.xianzhitech.ptt.service.dto.NearbyUser
//import com.xianzhitech.ptt.service.dto.RoomOnlineMemberUpdate
//import com.xianzhitech.ptt.service.dto.RoomSpeakerUpdate
//import com.xianzhitech.ptt.ui.KickOutActivity
//import com.xianzhitech.ptt.ui.room.RoomActivity
//import com.xianzhitech.ptt.util.*
//import org.slf4j.LoggerFactory
//import org.webrtc.*
//import rx.*
//import rx.android.schedulers.AndroidSchedulers
//import rx.schedulers.Schedulers
//import rx.subjects.BehaviorSubject
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.AtomicReference
//
//private val logger = LoggerFactory.getLogger("SignalServiceHandler")
//
//
//class SignalServiceHandler(private val appContext: Context,
//                           private val appComponent: AppComponent) {
//
//    private val loginStatusSubject = BehaviorSubject.create(LoginStatus.IDLE)
//    private val roomStateSubject = BehaviorSubject.create(RoomState.EMPTY)
//
//    val peekCurrentUserId: String?
//        get() = appComponent.preference.userSessionToken?.userId
//
//    val currentUserId : Observable<String?>
//        get() = appComponent.preference.userSessionTokenSubject.map { it?.userId }.distinctUntilChanged()
//
//    val loggedIn : Observable<Boolean>
//        get() = appComponent.preference.userSessionTokenSubject.map { it != null }.distinctUntilChanged()
//
//    private val mainThreadHandler = Handler(Looper.getMainLooper())
//
//    private var loginSubscription: Subscription? = null
//    private var syncContactSubscription : Subscription? = null // Part of login subscription
//
//    private val authTokenFactory = AuthTokenFactory()
//
//    private val signalService = SignalService(
//            appContext,
//            { authTokenFactory() },
//            DefaultSignalFactory(),
//            { getDeviceId() },
//            { retrieveAppParams(Constants.EMPTY_USER_ID) },
//            appComponent.httpClient,
//            appComponent.gson
//    )
//
//    val roomState: Observable<RoomState>
//        get()  = roomStateSubject
//    val roomStatus: Observable<RoomStatus>
//        get() = roomState.map { peekRoomState().status }.distinctUntilChanged()
//    val loginStatus: Observable<LoginStatus>
//        get() = loginStatusSubject.distinctUntilChanged()
//
//    val currentRoomId : Observable<String?>
//        get() = roomState.map { it.currentRoomId }.distinctUntilChanged()
//
//    val currentUserCache: BehaviorSubject<UserObject> = BehaviorSubject.create()
//
//    private var groupChatPeerConnection: PeerConnection? = null
//    private val groupChatViews = mutableListOf<GroupChatView>()
//    private var groupChatRemoteStream: MediaStream? = null
//    private var groupChatVideoCapturer: VideoCapturer? = null
//    var groupChatRoomId : String? = null
//        private set
//
//    init {
//        val token = appComponent.preference.userSessionToken
////        if (token != null) {
////            authTokenFactory.set(token.userId, token.password)
////            signalService.login().subscribe(object : CompletableSubscriber {
////                override fun onSubscribe(d: Subscription?) { }
////
////                override fun onError(e: Throwable?) {
////                    logger.e(e) { "Error logging in" }
////                }
////
////                override fun onCompleted() {
////                }
////            })
////        }
////
////        signalService.signals.observeOnMainThread().subscribeSimple { dispatchSignal(it) }
//    }
//
//    fun peekLoginStatus(): LoginStatus = loginStatusSubject.value
//    fun peekCurrentRoomId() : String? = roomStateSubject.value.currentRoomId
//    fun peekRoomState(): RoomState = roomStateSubject.value
//
//    fun login(loginName: String, loginPassword: String) : Completable {
//        return Completable.defer {
//            if (loginStatusSubject.value != LoginStatus.IDLE) {
//                return@defer Completable.complete()
//            }
//
//            authTokenFactory.set(loginName, loginPassword)
//            signalService.login()
//        }.subscribeOn(AndroidSchedulers.mainThread())
//                .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
//                .doOnError { logout() }
//    }
//
//    private fun getDeviceId() : Single<String> {
//        return Single.just(null)
//    }
//
//    private fun dispatchSignal(signal: Signal) {
//        when (signal) {
//            is ConnectionSignal -> onConnectionEvent(signal)
//            is RoomUpdateSignal -> onRoomUpdate(signal.room)
//            is UserKickOutSignal -> onUserKickOut()
//            is RoomKickOutSignal -> onRoomKickedOut(signal.roomId)
//            is RoomSpeakerUpdateSignal -> onRoomSpeakerUpdate(signal.update)
//            is RoomMessageSignal -> onReceiveRoomMessage(signal.message)
//            is RoomOnlineMemberUpdateSignal -> onRoomOnlineMemberUpdate(signal.update)
//            is RoomInviteSignal -> onReceiveInvitation(signal.invitation)
//            is UserLoggedInSignal -> onUserLoggedIn(signal.user)
//            is UserUpdatedSignal -> onUserUpdated(signal.user)
//            is UserLoginFailSignal -> onUserLoginFailed(signal.err)
//            is UpdateLocationSignal -> onUpdateLocation()
//            is IceCandidateSignal -> onReceiveIceCandidate(signal.iceCandidate)
//        }
//    }
//
//    private fun onReceiveRoomMessage(message: Message) {
//        mainThread {
//            appComponent.messageRepository.saveMessage(listOf(message)).execAsync().subscribeSimple()
//        }
//    }
//
//    private fun onReceiveIceCandidate(iceCandidate: IceCandidate) {
//        mainThread {
//            groupChatPeerConnection?.addIceCandidate(iceCandidate)
//        }
//    }
//
//    private fun onUpdateLocation() {
//    }
//
//    private fun onUserLoginFailed(err: Throwable) {
//        Answers.getInstance()
//                .logLogin(
//                        LoginEvent().apply {
//                            putSuccess(false)
//                            withUser(peekCurrentUserId, currentUserCache.value)
//                            putCustomAttribute("error", err.describeInHumanMessage(appContext).toString())
//                        }
//                )
////        logout()
//        Toast.makeText(appContext, err.describeInHumanMessage(appContext), Toast.LENGTH_SHORT).show()
//    }
//
//    private fun onConnectionEvent(signal: ConnectionSignal) {
//        when (signal.event) {
//            ConnectionEvent.CONNECTING -> loginStatusSubject += LoginStatus.LOGIN_IN_PROGRESS
//            ConnectionEvent.ERROR -> {
//                Answers.getInstance()
//                        .logCustom(CustomEvent("Connection error").apply {
//                            withUser(peekCurrentUserId, currentUserCache.value)
//                        })
//                loginStatusSubject += LoginStatus.IDLE
//            }
//        }
//    }
//
//    private fun onUserUpdated(user: UserObject) {
//        logger.i { "Updating user to $user" }
//        currentUserCache.onNext(user)
//        appComponent.userRepository.saveUsers(listOf(user)).execAsync().subscribeSimple()
//    }
//
//    private fun onUserLoggedIn(user: UserObject) {
//        loginStatusSubject += LoginStatus.LOGGED_IN
//
//        Answers.getInstance()
//                .logLogin(
//                        LoginEvent().apply {
//                            putSuccess(true)
//                            withUser(user.id, user)
//                        }
//                )
//
//        currentUserCache.onNext(user)
//        val firstTimeLogin = appComponent.preference.userSessionToken?.userId != user.id
//        appComponent.preference.userSessionToken = UserToken(user.id, authTokenFactory.password)
//
//        // Save user to database
//        appComponent.userRepository.saveUsers(listOf(user)).execAsync().subscribeSimple()
//
//        if (firstTimeLogin) {
//            logger.i { "Clearing room data for new user" }
//            appComponent.roomRepository.clear().execAsync(true).await()
//        }
//
//        if (syncContactSubscription == null) {
//            syncContactSubscription = loginStatusSubject
//                    .distinctUntilChanged()
//                    .filter { it == LoginStatus.LOGGED_IN }
//                    .switchMap { Observable.interval(0, Constants.SYNC_CONTACT_INTERVAL_MILLS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()) }
//                    .switchMap {
//                        val version = appComponent.preference.contactVersion
//                        logger.i { "Syncing contact version with localVersion=$version" }
//                        syncContact(version)
//                                .doOnError { logger.e(it) { "Error syncing contacts" } }
//                                .andThen(Observable.empty<Unit>())
//                    }
//                    .onErrorReturn { null }
//                    .subscribeSimple()
//        }
//
//        val rs = peekRoomState()
//        if (rs.status.inRoom && rs.currentRoomId != null) {
//            joinRoom(rs.currentRoomId, false, true).subscribeSimple()
//        }
//    }
//
//    private fun onRoomUpdate(newRoom: Room) {
//        appComponent.roomRepository.saveRooms(listOf(newRoom)).execAsync().subscribeSimple()
//    }
//
//    private fun retrieveAppParams(loginName: String): Single<AppConfig> {
//        return appComponent.appService.retrieveAppConfig(loginName)
//                .subscribeOn(Schedulers.io())
//                .doOnSuccess {
//                    // 将结果缓存起来, 以便无网络时使用
//                    appComponent.preference.lastAppParams = it
//                }
//                .onErrorResumeNext {
//                    // 出错了: 如果我们之前存有AppParam, 则使用那个, 否则就继续返回错误
//                    appComponent.preference.lastAppParams?.let { Single.just(it) } ?: Single.error(it)
//                }
//    }
//
//    fun syncContact(version : Long = -1L) : Completable {
//        val userId = peekCurrentUserId ?: return Completable.complete()
//        return SyncContactCommand(userId, authTokenFactory.password.toMD5(), version).send()
//                .flatMapCompletable {
//                    appComponent.contactRepository.replaceAllContacts(it.users, it.groups)
//                            .execAsync()
//                            .observeOnMainThread()
//                            .doOnCompleted {
//                                logger.d { "Got contact version ${it.version}" }
//                                appComponent.preference.contactVersion = it.version
//                            }
//                }
//    }
//
//    fun sendLocationData(locations : List<Location>) : Completable {
//        if (locations.isEmpty()) {
//            logger.i { "Sending 0 locations...Skipping" }
//            return Completable.complete()
//        }
//
//        val userId = peekCurrentUserId ?: return Completable.complete()
//        return UpdateLocationCommand(locations = locations, userId = userId).send()
//                .toCompletable()
//    }
//
//    fun sendMessage(msg : Message) : Single<Message> {
//        return SendMessageCommand(msg).send()
//                .doOnSuccess { message ->
//                    appComponent.messageRepository.saveMessage(listOf(message)).execAsync().subscribeSimple()
//                }
//    }
//
//    fun logout() {
//        mainThread {
//            val userId = peekCurrentUserId
//            val userName = currentUserCache.value?.name
//
//            val roomState = peekRoomState()
//            if (roomState.currentRoomId != null) {
//                quitRoom()
//            }
//
//            loginSubscription?.unsubscribe()
//            loginSubscription = null
//            syncContactSubscription?.unsubscribe()
//            syncContactSubscription = null
//            authTokenFactory.clear()
//            appComponent.preference.apply {
//                userSessionToken = null
//                lastExpPromptTime = null
//                contactVersion = -1
//                enableDownTime = false
//            }
//
//            loginStatusSubject += LoginStatus.IDLE
//            currentUserCache.onNext(null)
//            signalService.logout()
//
//            if (userId != null) {
//                Answers.getInstance()
//                        .logCustom(CustomEvent("ContactUser log out").apply {
//                            putCustomAttribute("userId", userId)
//                            putCustomAttribute("userName", userName)
//                        })
//            }
//        }
//    }
//
//    fun createRoom(groupIds : Collection<String> = emptyList(), userIds : Collection<String> = emptyList()): Single<Room> {
//        return Single.defer {
//            ensureLoggedIn()
//
//            val permissions = currentUserCache.value!!.permissions
//            if ((groupIds.isEmpty() && userIds.size == 1 && permissions.contains(Permission.MAKE_INDIVIDUAL_CALL).not()) ||
//                    (groupIds.isEmpty() && permissions.contains(Permission.MAKE_TEMPORARY_GROUP_CALL).not() &&
//                            userIds.filterNot { it == currentUserCache.value!!.id }.size > 1 )) {
//                throw StaticUserException(R.string.error_no_permission)
//            }
//
//            Answers.getInstance()
//                    .logCustom(CustomEvent("Create room").apply {
//                        withUser(peekCurrentUserId, currentUserCache.value)
//                        putCustomAttribute("numberOfGroups", groupIds.size)
//                        putCustomAttribute("numberOfUsers", userIds.size)
//                    })
//
//            CreateRoomCommand(groupIds = groupIds, extraMemberIds = userIds)
//                    .send()
//                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }
//
//        }.subscribeOn(AndroidSchedulers.mainThread())
//    }
//
//    fun waitForUserLogin() : Completable {
//        return Completable.defer {
//            when (loginStatusSubject.value) {
//                LoginStatus.LOGGED_IN -> return@defer Completable.complete()
//                LoginStatus.IDLE -> return@defer Completable.error(StaticUserException(R.string.error_unable_to_connect))
//                else -> {}
//            }
//
//            logger.i { "Waiting for user to log in..." }
//            Completable.complete()
//        }.subscribeOn(AndroidSchedulers.mainThread())
//    }
//
//    fun joinRoom(roomId: String, fromInvitation: Boolean, force : Boolean = false): Completable {
//        return waitForUserLogin()
//                .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
//                .andThen(appComponent.roomRepository.getRoom(roomId).getAsync())
//                .observeOnMainThread()
//                .flatMapCompletable { room ->
//                    if (room == null) {
//                        throw StaticUserException(R.string.error_room_not_exists)
//                    }
//
//                    Completable.create { subscriber : CompletableSubscriber ->
//                        val currRoomState = peekRoomState()
//                        val currentRoomId = currRoomState.currentRoomId
//                        if (currentRoomId == roomId && currRoomState.status != RoomStatus.IDLE && force.not()) {
//                            subscriber.onCompleted()
//                            return@create
//                        }
//
//                        val permissions = currentUserCache.value!!.permissions
//                        if ((room.associatedGroupIds.isEmpty() && room.extraMemberIds.size <= 2 && permissions.contains(Permission.MAKE_INDIVIDUAL_CALL).not()) ||
//                                (room.associatedGroupIds.isEmpty() && permissions.contains(Permission.MAKE_TEMPORARY_GROUP_CALL).not() &&
//                                        room.extraMemberIds.filterNot { it == currentUserCache.value!!.id }.size > 1 )) {
//                            subscriber.onError(StaticUserException(R.string.error_no_permission))
//                            return@create
//                        }
//
//                        if (currentRoomId != null && currentRoomId != roomId) {
//                            LeaveRoomCommand(currentRoomId, appComponent.preference.keepSession.not()).send()
//                        }
//
//                        roomStateSubject += RoomState.EMPTY.copy(status = RoomStatus.JOINING, currentRoomId = roomId)
//
//                        JoinRoomCommand(roomId, fromInvitation)
//                                .send()
//                                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
//                                .observeOn(AndroidSchedulers.mainThread())
//                                .flatMap { appComponent.roomRepository.saveRooms(listOf(it.room)).execAsync().toSingleDefault(it) }
//                                .observeOn(AndroidSchedulers.mainThread())
//                                .subscribe(object : SingleSubscriber<JoinRoomResult>() {
//                                    override fun onError(error: Throwable) {
//                                        subscriber.onError(error)
//
//                                        if (peekRoomState().currentRoomId == roomId) {
//                                            quitRoom()
//                                        }
//
//                                        if (error is KnownServerException &&
//                                                error.errorName == "room_not_exists") {
//                                            appComponent.roomRepository.removeRooms(listOf(roomId)).execAsync().subscribeSimple()
//                                        }
//
//                                        Answers.getInstance()
//                                                .logCustom(CustomEvent("Join room error").apply {
//                                                    withUser(peekCurrentUserId, currentUserCache.value)
//                                                    putCustomAttribute("roomId", roomId)
//                                                    putCustomAttribute("fromInvitation", fromInvitation.toString())
//                                                    putCustomAttribute("error", error.describeInHumanMessage(appContext).toString())
//                                                })
//                                    }
//
//                                    override fun onSuccess(value: JoinRoomResult) {
//                                        logger.d { "Join room result $value" }
//
//                                        val state = peekRoomState()
//                                        if (state.currentRoomId == value.room.id && state.status == RoomStatus.JOINING) {
//                                            val newStatus = if (value.speakerId == null) {
//                                                RoomStatus.JOINED
//                                            } else {
//                                                RoomStatus.ACTIVE
//                                            }
//
//                                            roomStateSubject += state.copy(status = newStatus,
//                                                    onlineMemberIds = state.onlineMemberIds + value.onlineMemberIds.toSet(),
//                                                    currentRoomInitiatorUserId = value.initiatorUserId,
//                                                    speakerId = value.speakerId,
//                                                    speakerPriority = value.speakerPriority,
//                                                    voiceServer = null)
//                                        }
//
//                                        Answers.getInstance()
//                                                .logCustom(CustomEvent("Join room success").apply {
//                                                    withUser(peekCurrentUserId, currentUserCache.value)
//                                                    putCustomAttribute("roomId", roomId)
//                                                    putCustomAttribute("fromInvitation", fromInvitation.toString())
//                                                })
//
//                                        subscriber.onCompleted()
//                                    }
//                                })
//
//                    }
//                }
//    }
//
//    fun quitRoom() {
//        mainThread {
//            if (loginStatusSubject.value == LoginStatus.LOGGED_IN) {
//                val roomId = peekRoomState().currentRoomId
//                if (roomId != null) {
//                    LeaveRoomCommand(roomId, appComponent.preference.keepSession.not()).send()
//
//                    Answers.getInstance()
//                            .logCustom(CustomEvent("Quit room").apply {
//                                withUser(peekCurrentUserId, currentUserCache.value)
//                                putCustomAttribute("roomId", roomId)
//                            })
//                }
//            }
//
//            roomStateSubject += RoomState.EMPTY
//        }
//    }
//
//    fun startGroupChat(roomId : String) {
//        if (groupChatPeerConnection != null) {
//            groupChatPeerConnection!!.close()
//            groupChatPeerConnection!!.dispose()
//        }
//
//        JoinGroupChatCommand(roomId)
//                .send()
//                .observeOnMainThread()
//                .subscribeSimple {
//                    val factory = PeerConnectionFactory(PeerConnectionFactory.Options())
//                    val mediaConstraints = MediaConstraints()
//                    mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//                    mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//                    val p = factory.createPeerConnection(PeerConnection.RTCConfiguration(listOf(
////                            PeerConnection.IceServer("stun:121.43.152.43:3478"),
////                            PeerConnection.IceServer("stun:123.207.148.55:3478"),
////                            PeerConnection.IceServer("stun:121.41.22.11:3478")
////                            PeerConnection.IceServer("turn:123.207.148.55:3478?transport=udp", "feihe1234", "feihe"),
////                            PeerConnection.IceServer("turn:192.168.0.167?transport=udp", "ptt", "ptt1234")
//                    )).apply {
//                        this.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
//                        this.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
//                    },
////                    val p = factory.createPeerConnection(emptyList(),
//                            mediaConstraints,
//                            object : SimplePeerConnectionObserver() {
//                                override fun onIceCandidate(p0: IceCandidate) {
//                                    super.onIceCandidate(p0)
//                                    AddIceCandidateCommand(p0).send().subscribeSimple()
//                                }
//
//                                override fun onAddStream(p0: MediaStream) {
//                                    super.onAddStream(p0)
//
//                                    mainThread {
//                                        groupChatRemoteStream = p0
//                                        val videoTrack = p0.videoTracks.first
//                                        videoTrack.setEnabled(groupChatViews.isNotEmpty())
//                                        groupChatViews.forEach {
//                                            videoTrack.addRenderer(it.remoteRenderer)
//                                        }
//
//                                        p0.audioTracks.first.setEnabled(true)
//                                    }
//                                }
//
//                                override fun onRemoveStream(p0: MediaStream) {
//                                    super.onRemoveStream(p0)
//
//                                    mainThread {
//                                        if (groupChatRemoteStream == p0) {
//                                            val videoTrack = groupChatRemoteStream!!.videoTracks.first
//                                            groupChatViews.forEach { videoTrack.removeRenderer(it.remoteRenderer) }
//                                            groupChatRemoteStream = null
//                                        }
//                                    }
//                                }
//                            })
//
//                    val enumerator = Camera1Enumerator()
//                    val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.first()
//                    groupChatVideoCapturer = Camera1Capturer(cameraName, SimpleCameraEventsHandler(), false)
//
//                    val mediaStream = factory.createLocalMediaStream("local-stream")
//                    val audioTrack = factory.createAudioTrack("audio", factory.createAudioSource(MediaConstraints()))
//                    val videoTrack = factory.createVideoTrack("video", factory.createVideoSource(groupChatVideoCapturer))
//                    mediaStream.addTrack(audioTrack)
//                    mediaStream.addTrack(videoTrack)
//
//                    p.addStream(mediaStream)
//
//                    p.createOffer(object : SimpleSdpObserver() {
//                        override fun onCreateSuccess(offer: SessionDescription) {
//                            super.onCreateSuccess(offer)
//
//                            p.setLocalDescription(SimpleSdpObserver(), offer)
//
//                            OfferGroupChatCommand(offer.description)
//                                    .send()
//                                    .subscribeSimple { p.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, it)) }
//                        }
//                    }, MediaConstraints())
//
//                    groupChatVideoCapturer!!.startCapture(512, 512, 30)
//                    groupChatPeerConnection = p
//                    groupChatRoomId = roomId
//                }
//    }
//
//    fun quitGroupChat() {
//        if (groupChatRoomId != null) {
//            QuitGroupChatCommand(groupChatRoomId!!).send().subscribeSimple()
//        }
//        groupChatViews.forEach { detachFromVideoChat(it)}
//        groupChatVideoCapturer?.dispose()
//        groupChatPeerConnection?.let {
//            it.close()
//            it.dispose()
//        }
//
//        groupChatVideoCapturer = null
//        groupChatRemoteStream = null
//        groupChatPeerConnection = null
//        groupChatRoomId = null
//    }
//
//    fun attachToVideoChat(chatView: GroupChatView) {
//        if (groupChatViews.contains(chatView).not()) {
//            groupChatViews.add(chatView)
//            groupChatRemoteStream?.videoTracks?.first?.addRenderer(chatView.remoteRenderer)
//        }
//    }
//
//    fun detachFromVideoChat(chatView: GroupChatView) {
//        if (groupChatViews.remove(chatView)) {
//            groupChatRemoteStream?.videoTracks?.first?.removeRenderer(chatView.remoteRenderer)
//        }
//    }
//
//    fun requestMic(): Single<Boolean> {
//        return Single.defer<Boolean> {
//            ensureLoggedIn()
//
//            val roomState = peekRoomState()
//            val userId = peekCurrentUserId
//            if (roomState.speakerId == userId) {
//                return@defer Single.just(true)
//            }
//
//            if (currentUserCache.value!!.permissions.contains(Permission.SPEAK).not()) {
//                throw StaticUserException(R.string.speak_forbidden)
//            }
//
//            if (roomState.canRequestMic(currentUserCache.value!!).not()) {
//                throw IllegalStateException("Can't request mic in room state $roomState")
//            }
//
//            logger.d { "Requesting mic... $roomState" }
//
//            roomStateSubject += roomState.copy(status = RoomStatus.REQUESTING_MIC)
//            RequestMicCommand(roomState.currentRoomId!!)
//                    .send()
//                    .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .doOnSuccess {
//                        if (it) {
//                            val newRoomState = peekRoomState()
//                            logger.d { "Successfully requested mic in $newRoomState" }
//                            if ((newRoomState.status == RoomStatus.REQUESTING_MIC || newRoomState.status == RoomStatus.ACTIVE) &&
//                                    newRoomState.currentRoomId == roomState.currentRoomId &&
//                                    peekCurrentUserId == userId) {
//                                roomStateSubject += newRoomState.copy(
//                                        speakerId = userId,
//                                        speakerPriority = currentUserCache.value!!.priority,
//                                        status = RoomStatus.ACTIVE)
//                            }
//                        }
//                    }
//                    .doOnError {
//                        val newRoomState = peekRoomState()
//                        if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
//                                newRoomState.currentRoomId == roomState.currentRoomId &&
//                                peekCurrentUserId == userId) {
//                            val newStatus = if (newRoomState.speakerId == null) {
//                                RoomStatus.JOINED
//                            } else {
//                                RoomStatus.ACTIVE
//                            }
//                            roomStateSubject += newRoomState.copy(status = newStatus)
//                        }
//                    }
//        }.subscribeOn(AndroidSchedulers.mainThread())
//    }
//
//    fun releaseMic() {
//        mainThread {
//            val state = peekRoomState()
//            logger.d { "Releasing mic in $state" }
//
//            if (state.currentRoomId != null &&
//                    (state.speakerId == peekCurrentUserId || state.status == RoomStatus.REQUESTING_MIC)) {
//                ReleaseMicCommand(state.currentRoomId).send()
//                roomStateSubject += state.copy(speakerId = null, speakerPriority = null, status = RoomStatus.JOINED)
//            }
//        }
//    }
//
//    fun inviteRoomMembers(roomId: String) : Single<Int> {
//        return Single.defer {
//            ensureLoggedIn()
//            val state = peekRoomState()
//
//            if (state.currentRoomId == roomId) {
//                Answers.getInstance()
//                        .logCustom(CustomEvent("Manually invite room members").apply {
//                            withUser(peekCurrentUserId, currentUserCache.value)
//                            putCustomAttribute("roomId", roomId)
//                        })
//
//                InviteRoomMemberCommand(roomId).send()
//            }
//            else {
//                throw StaticUserException(R.string.error_room_not_exists)
//            }
//        }.subscribeOn(AndroidSchedulers.mainThread())
//    }
//
//    fun retrieveRoomInfo(roomId: String): Single<Room> {
//        //TODO:
//        return Single.create {  }
//    }
//
//    fun updateRoomMembers(roomId: String, roomMemberIds: Collection<String>): Completable {
//        return Completable.defer {
//            ensureLoggedIn()
//
//            Answers.getInstance()
//                    .logCustom(CustomEvent("Add new room members").apply {
//                        withUser(peekCurrentUserId, currentUserCache.value)
//                        putCustomAttribute("roomId", roomId)
//                        putCustomAttribute("numberOfInvitees", roomMemberIds.size)
//                    })
//
//            AddRoomMembersCommand(roomId, roomMemberIds)
//                    .send()
//                    .flatMap { appComponent.roomRepository.saveRooms(listOf(it)).execAsync().toSingleDefault(it) }
//                    .toCompletable()
//        }.subscribeOn(AndroidSchedulers.mainThread())
//    }
//
//    fun searchNearbyUsers(bounds : LatLngBounds): Observable<List<NearbyUser>> {
//        return Observable.interval(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
//                .onBackpressureLatest()
//                .switchMap { FindNearbyPeopleCommand(bounds).send().toObservable() }
//    }
//
//    fun changePassword(oldPassword: String, newPassword: String): Completable {
//        return Completable.defer {
//            ensureLoggedIn()
//            val currUserId = peekCurrentUserId!!
//            ChangePasswordCommand(currUserId, oldPassword.toMD5(), newPassword.toMD5())
//                    .send()
//                    .doOnSuccess {
//                        Answers.getInstance()
//                                .logCustom(CustomEvent("Change password").apply {
//                                    withUser(peekCurrentUserId, currentUserCache.value)
//                                })
//
//                        mainThread {
//                            if (peekCurrentUserId == currUserId) {
//                                authTokenFactory.setPassword(newPassword)
//                                appComponent.preference.userSessionToken = UserToken(currUserId, newPassword)
//                            }
//                        }
//                    }
//                    .toCompletable()
//        }
//    }
//
//    private fun ensureLoggedIn() {
//        if (loginStatusSubject.value != LoginStatus.LOGGED_IN || currentUserCache.value == null) {
//            throw StaticUserException(R.string.error_unable_to_connect)
//        }
//    }
//
//    private fun onUserKickOut() {
//        mainThread {
//            Answers.getInstance()
//                    .logCustom(CustomEvent("ContactUser kicked out").apply {
//                        withUser(peekCurrentUserId, currentUserCache.value)
//                    })
//
//            logout()
//            val intent = Intent(appContext, KickOutActivity::class.java)
//            appComponent.activityProvider.currentStartedActivity?.startActivity(intent)
//                    ?: appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
//        }
//    }
//
//    private fun onRoomKickedOut(roomId : String) {
//        mainThread {
//            if (roomId == peekCurrentRoomId()) {
//                quitRoom()
//                Toast.makeText(appContext, R.string.room_kicked_out, Toast.LENGTH_LONG).show()
//                (appComponent.activityProvider.currentStartedActivity as? RoomActivity)?.finish()
//            }
//        }
//    }
//
//    private fun onRoomSpeakerUpdate(update: RoomSpeakerUpdate) {
//        mainThread {
//            val state = peekRoomState()
//            if (state.status == RoomStatus.IDLE || state.currentRoomId != update.roomId) {
//                return@mainThread
//            }
//
//            roomStateSubject += state.copy(
//                    speakerId = update.speakerId,
//                    speakerPriority = update.speakerPriority,
//                    status = if (update.speakerId == null) RoomStatus.JOINED else RoomStatus.ACTIVE)
//        }
//    }
//
//    private fun onRoomOnlineMemberUpdate(update: RoomOnlineMemberUpdate) {
//        mainThread {
//            val state = peekRoomState()
//            if (state.status == RoomStatus.IDLE ||
//                    state.currentRoomId != update.roomId) {
//                return@mainThread
//            }
//
//            logger.d { "Online member IDs updated to ${update.memberIds}, curr state = $state" }
//
//            roomStateSubject += state.copy(onlineMemberIds = update.memberIds.toSet())
//        }
//    }
//
//    private fun onReceiveInvitation(invite: RoomInvitation) {
//        Answers.getInstance()
//                .logCustom(CustomEvent("Receive room invitation").apply {
//                    withUser(peekCurrentUserId, currentUserCache.value)
//                    putCustomAttribute("fromUserId", invite.inviterId)
//                    putCustomAttribute("fromUserPriority", invite.inviterPriority)
//                    putCustomAttribute("forceInvite", invite.force.toString())
//                })
//
//        appContext.sendBroadcast(Intent(ACTION_ROOM_INVITATION).putExtra(SignalServiceHandler.Companion.EXTRA_INVITATION, invite))
//    }
//
//    private fun mainThread(runnable: () -> Unit) {
//        if (Thread.currentThread() == Looper.getMainLooper().thread) {
//            runnable()
//        } else {
//            mainThreadHandler.post(runnable)
//        }
//    }
//
//    private fun <R, C : Command<R, *>> C.send() : Single<R> {
//        signalService.sendCommand(this)
//        return getAsync()
//    }
//    companion object {
//
//        const val ACTION_ROOM_INVITATION = "SignalService.Room"
//        const val EXTRA_INVITATION = "extra_ri"
//
//    }
//
//}
//
//
//
//class ForceUpdateException(val appParams: AppConfig) : RuntimeException()
//
//
//private class AuthTokenFactory() {
//    private val auth = AtomicReference<Pair<String, String>>()
//    val password : String
//        get() = auth.get().second
//
//    operator fun invoke() : String {
//        return ""
//    }
//
//    fun set(loginName : String, password : String) {
//        auth.set(loginName to password)
//    }
//
//    fun setPassword(newPassword: String) : Pair<String, String> {
//        val (name, oldPass) = auth.get() ?: throw NullPointerException()
//        val result = name to newPassword
//        auth.set(result)
//        return result
//    }
//
//    fun clear() {
//        auth.set(null)
//    }
//
//
//
//    companion object {
//    }
//}
