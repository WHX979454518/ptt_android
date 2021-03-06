package com.xianzhitech.ptt.broker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.os.SystemClock
import com.baidu.mapapi.model.LatLngBounds
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.dto.LastLocationByUser
import com.xianzhitech.ptt.api.dto.MessageQuery
import com.xianzhitech.ptt.api.dto.MessageQueryResult
import com.xianzhitech.ptt.api.dto.UserLocation
import com.xianzhitech.ptt.api.event.*
import com.xianzhitech.ptt.data.*
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.handler.GroupChatView
import com.xianzhitech.ptt.util.InputStreamReadReporter
import com.xianzhitech.ptt.util.SimpleCameraEventsHandler
import com.xianzhitech.ptt.util.SimplePeerConnectionObserver
import com.xianzhitech.ptt.util.SimpleSdpObserver
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Okio
import org.slf4j.LoggerFactory
import org.webrtc.Camera1Capturer
import org.webrtc.Camera1Enumerator
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit


class SignalBroker(private val appComponent: AppComponent,
                   private val appContext: Context) {
    private val logger = LoggerFactory.getLogger("SignalBroker")

    private val signalApi = SignalApi(appComponent, appContext)

    val events: Observable<Event>
        get() = signalApi.events.map { it.second }

    val connectionState = signalApi.connectionState
    val currentUser = signalApi.currentUser

    val currentVideoRoomId: BehaviorSubject<Optional<String>> = BehaviorSubject.createDefault(Optional.absent<String>())
    val currentWalkieRoomState: BehaviorSubject<RoomState> = BehaviorSubject.createDefault(RoomState.EMPTY)

    val currentWalkieRoomId: Observable<Optional<String>>
        get() = currentWalkieRoomState.map { it.currentRoomId.toOptional() }.distinctUntilChanged()

    val enterprise: Observable<ContactEnterprise>
        get() {
            return combineLatest(
                    appComponent.storage.getAllUsers(),
                    signalApi.getAllDepartments().toObservable()) { users, departments ->
                val departmentParentMap = hashMapOf<String?, MutableCollection<ContactDepartment>>()
                val departmentMap = departments.associateBy(ContactDepartment::id)

                departments.forEach { department ->
                    departmentParentMap.getOrPut(department.parentObjectId) { linkedSetOf() }.add(department)
                }

                departmentParentMap.entries.forEach { (parentObjectId, departmentList) ->
                    if (parentObjectId != null) {
                        departmentMap[parentObjectId]?.children?.addAll(departmentList)
                    }
                }

                val directUsers = linkedSetOf<ContactUser>()

                users.forEach { user ->
                    user.parentObjectId?.let { departmentMap[it]?.members?.add(user) } ?: directUsers.add(user)
                }

                val currUser = currentUser.value.orNull()
                ContactEnterprise(
                        departments = departmentParentMap[currUser?.enterpriseObjectId] ?: emptyList<ContactDepartment>(),
                        directUsers = directUsers,
                        name = currUser?.enterpriseName ?: ""
                )
            }
        }

    val onlineUserIds: Observable<Set<String>>
        get() = signalApi.onlineUserIds

    private var joinWalkieDisposable: Disposable? = null

    private var videoChatPeerConnection: PeerConnection? = null
    private var videoChatRemoteStream: MediaStream? = null
    private var videoChatVideoCapturer: Camera1Capturer? = null
    private var videoChatLocalStream : MediaStream? = null
    private val groupChatViews : MutableList<GroupChatView> = arrayListOf()

    var videoChatVideoOn : Boolean = false
    set(value) {
        if (field != value) {
            field = value
            videoChatLocalStream?.videoTracks?.first()?.setEnabled(value)
            videoChatRemoteStream?.videoTracks?.first()?.setEnabled(value)
            if (value) {
                videoChatVideoCapturer?.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_RATE)
            } else {
                videoChatVideoCapturer?.stopCapture()
            }
        }
    }

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

        signalApi.events
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (name, event) -> onSignalEvent(name, event) }
    }

    fun login(name: String, password: String): Completable {
        Preconditions.checkState(isLoggedIn.not())

        @Suppress("UNCHECKED_CAST")
        return Completable.fromObservable((events as Observable<Any>)
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
        if (currentVideoRoomId.value.isPresent) {
            quitVideoRoom()
        }

        if (currentWalkieRoomState.value.currentRoomId != null) {
            quitWalkieRoom()
        }

        appComponent.preference.contactVersion = -1
        appComponent.storage.clear().logErrorAndForget().subscribe()
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

    fun getRoomPermissionException(room: Room): NoPermissionException? {
        val user = currentUser.value.orNull() ?: return null

        if (room.extraMemberIds.without(user.id).isNotEmpty()) {
            if (room.extraMemberIds.size <= 2) {
                return if (user.hasPermission(Permission.CALL_INDIVIDUAL).not()) {
                    NoPermissionException(Permission.CALL_INDIVIDUAL)
                } else {
                    null
                }
            }

            return if (user.hasPermission(Permission.CALL_TEMP_GROUP).not()) {
                NoPermissionException(Permission.CALL_TEMP_GROUP)
            } else {
                null
            }
        }

        return null
    }

    fun hasRoomPermission(room: Room): Boolean {
        return getRoomPermissionException(room) == null
    }

    private fun onSignalEvent(action: String, event: Event) {
        logger.i { "on signal event: $event" }

        when (event) {
            is WalkieRoomActiveInfoUpdateEvent -> {
                val state = currentWalkieRoomState.value
                if (state.currentRoomId == event.roomId) {
                    currentWalkieRoomState.onNext(state.copy(
                            speakerId = event.speakerId,
                            speakerStartTime = if (state.speakerId == null && event.speakerId != null) SystemClock.elapsedRealtime() else state.speakerStartTime,
                            speakerPriority = event.speakerPriority,
                            onlineMemberIds = event.onlineMemberIds
                    ))
                }
            }

            is WalkieRoomSpeakerUpdateEvent -> {
                val state = currentWalkieRoomState.value
                if (state.currentRoomId == event.roomId) {
                    currentWalkieRoomState.onNext(state.copy(
                            speakerStartTime = SystemClock.elapsedRealtime(),
                            speakerId = event.speakerId,
                            speakerPriority = event.speakerPriority
                    ))

                    recordWalkieRoomActive(event.roomId)
                }
            }

            is RoomKickOutEvent -> {
                quitWalkieRoom(false)
            }

            is CurrentUser -> {
                currentUser.onNext(event.toOptional())
            }

            is IceCandidateEvent -> {
                videoChatPeerConnection?.addIceCandidate(event.toWebrtc())
            }

            is Message -> {
                appComponent.storage.getRoom(event.roomId)
                        .firstOrError()
                        .flatMapCompletable { room ->
                            if (room.isAbsent) {
                                updateRoom(event.roomId).toCompletable()
                            } else {
                                Completable.complete()
                            }
                        }
                        .andThen(appComponent.storage.saveMessage(event))
                        .doOnSuccess {
                            if (it.hasRead.not()) {
                                appContext.sendBroadcast(Intent(action)
                                        .setPackage(appContext.packageName)
                                        .putExtra(EXTRA_EVENT, event as Serializable))
                            }
                        }
                        .toMaybe()
                        .logErrorAndForget()
                        .subscribe()
            }

            is Room -> {
                appComponent.storage.saveRoom(event)
                        .toMaybe()
                        .logErrorAndForget()
                        .subscribe()
            }
        }

        if (event is UserKickedOutEvent || event is WalkieRoomInvitationEvent || event is LoginFailedEvent) {
            val intent = Intent(action).setPackage(appContext.packageName)
            @Suppress("USELESS_CAST")
            when (event) {
                is Parcelable -> intent.putExtra(EXTRA_EVENT, event as Parcelable)
                else -> intent.putExtra(EXTRA_EVENT, event)
            }
            appContext.sendBroadcast(intent)
        }
    }


    fun joinWalkieRoom(roomId: String, fromInvitation: Boolean): Completable {
        return Completable.defer {
            if (peekVideoRoomId() != null) {
                logger.i { "Quitting video room before joining walkie room" }
                quitVideoRoom()
            }

            if (peekWalkieRoomId() == roomId) {
                logger.w { "Joining same room" }
            }

            if (peekWalkieRoomId() != null) {
                logger.i { "Quiting walkie room before joining different room $roomId" }
                quitWalkieRoom()
            }

            currentWalkieRoomState.onNext(RoomState.EMPTY.copy(status = RoomStatus.JOINING, currentRoomId = roomId))

            appComponent.storage.getRoom(roomId)
                    .firstOrError()
                    .flatMap {
                        if (it.isPresent.not()) {
                            throw NoSuchRoomException(roomId)
                        }

                        val exception = getRoomPermissionException(it.get())
                        if (exception != null) {
                            throw exception
                        }

                        signalApi.joinWalkieRoom(roomId, fromInvitation)
                    }
                    .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .flatMap { response ->
                        appComponent.storage.saveRoom(response.room).map { response }
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        if (peekWalkieRoomId() == roomId) {
                            quitWalkieRoom()
                        }

                        if (it is ServerException && it.name == "room_not_exists") {
                            appComponent.storage.removeRoom(roomId).logErrorAndForget().subscribe()
                        }

                        it.toast()
                    }
                    .doOnSuccess { (room, initiatorUserId, onlineMemberIds, speakerId, speakerPriority, voiceServerConfig) ->
                        val currState = currentWalkieRoomState.value

                        if (room.id == currState.currentRoomId) {
                            recordWalkieRoomActive(roomId)

                            sendMessage(createMessage(roomId, MessageType.NOTIFY_JOIN_ROOM)).toCompletable().logErrorAndForget().subscribe()

                            currentWalkieRoomState.onNext(currState.copy(
                                    status = if (peekUserId() == speakerId) RoomStatus.ACTIVE else RoomStatus.JOINED,
                                    speakerId = speakerId,
                                    speakerStartTime = SystemClock.elapsedRealtime(),
                                    speakerPriority = speakerPriority,
                                    currentRoomInitiatorUserId = initiatorUserId,
                                    onlineMemberIds = onlineMemberIds,
                                    voiceServer = voiceServerConfig
                            ))
                        }
                    }
                    .toCompletable()
        }
    }

    private fun recordWalkieRoomActive(roomId: String) {
        appComponent.storage.updateRoomLastWalkieActiveTime(roomId).logErrorAndForget().subscribe()
    }

    fun quitWalkieRoom(askOthersToLeave: Boolean = appComponent.preference.keepSession.not()) {
        joinWalkieDisposable?.dispose()
        joinWalkieDisposable = null

        val walkieRoomId = peekWalkieRoomId()

        currentWalkieRoomState.onNext(RoomState.EMPTY)

        if (walkieRoomId != null) {
            recordWalkieRoomActive(walkieRoomId)
            sendMessage(createMessage(walkieRoomId, MessageType.NOTIFY_QUIT_ROOM)).toCompletable().logErrorAndForget().subscribe()
            signalApi.leaveWalkieRoom(walkieRoomId, askOthersToLeave)
                    .logErrorAndForget()
                    .subscribe()
        }
    }

    fun grabWalkieMic(): Single<Boolean> {
        return Single.defer {
            val currentUser = currentUser.value.get()
            val roomState = currentWalkieRoomState.value

            Preconditions.checkState(roomState.status == RoomStatus.JOINED || roomState.status == RoomStatus.ACTIVE)
            Preconditions.checkState(roomState.currentRoomId != null && roomState.status != RoomStatus.REQUESTING_MIC)

            if (roomState.speakerId == currentUser.id) {
                return@defer Single.just(true)
            }

            if (currentUser.hasPermission(Permission.SPEAK).not()) {
                throw NoPermissionException(Permission.SPEAK)
            }


            if (roomState.speakerId != null && roomState.speakerPriority!! <= currentUser.priority) {
                return@defer Single.just(false)
            }

            currentWalkieRoomState.onNext(roomState.copy(status = RoomStatus.REQUESTING_MIC))
            signalApi.grabWalkieMic(roomState.currentRoomId!!)
                    .timeout(Constants.REQUEST_MIC_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSuccess { hasMic ->
                        if (hasMic) {
                            val newRoomState = currentWalkieRoomState.value
                            if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
                                    newRoomState.currentRoomId == roomState.currentRoomId &&
                                    peekUserId() == currentUser.id)
                                recordWalkieRoomActive(newRoomState.currentRoomId)
                            sendMessage(createMessage(newRoomState.currentRoomId!!, MessageType.NOTIFY_GRAB_MIC)).toCompletable().logErrorAndForget().subscribe()
                            currentWalkieRoomState.onNext(newRoomState.copy(
                                    speakerId = currentUser.id,
                                    speakerPriority = currentUser.priority,
                                    speakerStartTime = SystemClock.elapsedRealtime(),
                                    status = RoomStatus.ACTIVE
                            ))
                        }
                    }
                    .doOnError {
                        val newRoomState = currentWalkieRoomState.value
                        if (newRoomState.status == RoomStatus.REQUESTING_MIC &&
                                newRoomState.currentRoomId == roomState.currentRoomId &&
                                peekUserId() == currentUser.id) {
                            val newStatus = if (newRoomState.speakerId == null) {
                                RoomStatus.JOINED
                            } else {
                                RoomStatus.ACTIVE
                            }

                            currentWalkieRoomState.onNext(newRoomState.copy(status = newStatus))
                        }
                    }
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    fun releaseMic() {
        Completable.defer {
            val roomState = currentWalkieRoomState.value

            if (roomState.currentRoomId != null &&
                    (roomState.speakerId == peekUserId()) || roomState.status == RoomStatus.REQUESTING_MIC) {
                currentWalkieRoomState.onNext(roomState.copy(speakerPriority = null, speakerId = null, status = RoomStatus.JOINED))
                recordWalkieRoomActive(roomState.currentRoomId!!)
                sendMessage(createMessage(roomState.currentRoomId, MessageType.NOTIFY_RELEASE_MIC)).toCompletable().logErrorAndForget().subscribe()
                signalApi.releaseWalkieMic(roomState.currentRoomId)
            } else {
                Completable.complete()
            }
        }.subscribeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe()
    }

    fun joinVideoRoom(roomId: String, audioOnly: Boolean): Completable {
        return signalApi.joinVideoChat(roomId)
                .doOnSubscribe {
                    videoChatPeerConnection?.let {
                        it.close()
                        it.dispose()
                    }

                    quitWalkieRoom()
                    videoChatVideoOn = audioOnly.not()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    sendMessage(createMessage(roomId, MessageType.NOTIFY_START_VIDEO_CHAT)).toMaybe().logErrorAndForget().subscribe()

                    val factory = PeerConnectionFactory(PeerConnectionFactory.Options())
                    val mediaConstraints = MediaConstraints()
                    mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    val p = factory.createPeerConnection(PeerConnection.RTCConfiguration(listOf(
                            //                            PeerConnection.IceServer("stun:121.43.152.43:3478"),
//                            PeerConnection.IceServer("stun:123.207.148.55:3478"),
//                            PeerConnection.IceServer("stun:121.41.22.11:3478")
//                            PeerConnection.IceServer("turn:123.207.148.55:3478?transport=udp", "feihe1234", "feihe"),
//                            PeerConnection.IceServer("turn:192.168.0.167?transport=udp", "ptt", "ptt1234")
                    )).apply {
                        this.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                        this.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                    },
                            //                    val p = factory.createPeerConnection(emptyList(),
                            mediaConstraints,
                            object : SimplePeerConnectionObserver() {
                                override fun onIceCandidate(iceCandidate: IceCandidate) {
                                    super.onIceCandidate(iceCandidate)

                                    signalApi.sendIceCandidate(iceCandidate)
                                            .logErrorAndForget()
                                            .subscribe()
                                }

                                override fun onAddStream(p0: MediaStream) {
                                    super.onAddStream(p0)

                                    AndroidSchedulers.mainThread().scheduleDirect {
                                        videoChatRemoteStream = p0
                                        val videoTrack = p0.videoTracks.first
                                        videoTrack.setEnabled(groupChatViews.isNotEmpty())
                                        groupChatViews.forEach {
                                            videoTrack.addRenderer(it.remoteRenderer)
                                        }

                                        p0.audioTracks.first.setEnabled(true)
                                    }
                                }

                                override fun onRemoveStream(p0: MediaStream) {
                                    super.onRemoveStream(p0)

                                    AndroidSchedulers.mainThread().scheduleDirect {
                                    if (videoChatRemoteStream == p0) {
                                            val videoTrack = videoChatRemoteStream!!.videoTracks.first
                                            groupChatViews.forEach { videoTrack.removeRenderer(it.remoteRenderer) }
                                            videoChatRemoteStream = null
                                        }
                                    }
                                }
                            })

                    val enumerator = Camera1Enumerator()
                    val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.first()
                    videoChatVideoCapturer = Camera1Capturer(cameraName, SimpleCameraEventsHandler(), false)

                    val mediaStream = factory.createLocalMediaStream("local-stream")
                    videoChatLocalStream = mediaStream
                    val audioTrack = factory.createAudioTrack("audio", factory.createAudioSource(MediaConstraints()))
                    val videoTrack = factory.createVideoTrack("video", factory.createVideoSource(videoChatVideoCapturer))
                    mediaStream.addTrack(audioTrack)
                    mediaStream.addTrack(videoTrack)

                    videoTrack.setEnabled(audioOnly.not())

                    p.addStream(mediaStream)

                    p.createOffer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(offer: SessionDescription) {
                            super.onCreateSuccess(offer)

                            p.setLocalDescription(SimpleSdpObserver(), offer)

                            signalApi.offerVideoChat(offer.description)
                                    .toMaybe()
                                    .logErrorAndForget()
                                    .subscribe {
                                        p.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, it))
                                    }
                        }
                    }, MediaConstraints())

                    if (audioOnly.not()) {
                        videoChatVideoCapturer!!.startCapture(512, 512, 30)
                    }

                    videoChatPeerConnection = p
                    currentVideoRoomId.onNext(roomId.toOptional())

                }
    }

    fun quitVideoRoom() {
        currentVideoRoomId.value.orNull()?.let {
            signalApi.quitVideoChat(it).logErrorAndForget().subscribe()

            sendMessage(createMessage(it, MessageType.NOTIFY_END_VIDEO_CHAT)).toMaybe().logErrorAndForget().subscribe()
        }

        groupChatViews.forEach(this::detachFromVideoChat)
        videoChatVideoCapturer?.dispose()
        videoChatPeerConnection?.let {
            it.close()
            it.dispose()
        }

        videoChatVideoCapturer = null
        videoChatRemoteStream = null
        videoChatPeerConnection = null
        videoChatLocalStream = null
        currentVideoRoomId.onNext(Optional.absent())
    }

    fun attachToVideoChat(chatView: GroupChatView) {
        if (groupChatViews.contains(chatView).not()) {
            groupChatViews.add(chatView)
            videoChatRemoteStream?.videoTracks?.firstOrNull()?.addRenderer(chatView.remoteRenderer)
        }
    }

    fun detachFromVideoChat(chatView: GroupChatView) {
        if (groupChatViews.remove(chatView)) {
            videoChatRemoteStream?.videoTracks?.firstOrNull()?.removeRenderer(chatView.remoteRenderer)
        }
    }

    fun peekUserId(): String? = currentUser.value.orNull()?.id

    fun peekVideoRoomId(): String? = currentVideoRoomId.value.orNull()
    fun peekWalkieRoomId(): String? = currentWalkieRoomState.value.currentRoomId

    fun sendMessage(message: Message): Single<Message> {
        return appComponent.storage.saveMessage(message)
                .flatMap(signalApi::sendMessage)
                .flatMap(appComponent.storage::saveMessage)
    }

    fun createMessage(roomId: String, type: String, body: MessageBody? = null): Message {
        val entity = MessageEntity()
        entity.setSendTime(Date())
        entity.setLocalId(UUID.randomUUID().toString())
        entity.setRoomId(roomId)
        entity.setSenderId(currentUser.value.get().id)
        entity.setType(type)
        entity.setBody(body)
        return entity
    }

    fun createRoom(userIds: List<String> = emptyList(),
                   groupIds: List<String> = emptyList()): Single<Room> {
        return signalApi.createRoom(userIds, groupIds)
                .flatMap(appComponent.storage::saveRoom)
    }

    fun sendLocationData(locations: List<Location>): Completable {
        logger.i { "Sending ${locations.size} location data" }
        return signalApi.sendLocationData(locations)
    }

    fun getLastLocationByUserIds(userIds: List<String>): Maybe<List<LastLocationByUser>> {
        return signalApi.getLastLocationByUserIds(userIds)
    }

    fun updateRoom(roomId: String): Single<Room> {
        return signalApi.getRoom(roomId)
                .flatMap(appComponent.storage::saveRoom)
    }

    fun addRoomMembers(roomId: String, newMemberIds: List<String>): Single<Room> {
        return signalApi.addRoomMembers(roomId, newMemberIds)
                .flatMap(appComponent.storage::saveRoom)
                .doOnSuccess {
                    sendMessage(createMessage(roomId, MessageType.NOTIFY_ADDED_ROOM_MEMBERS, AddRoomMembersMessageBody(newMemberIds)))
                            .toCompletable()
                            .logErrorAndForget()
                            .subscribe()
                }
    }

    fun findNearbyPeople(topLeft : LatLng, bottomRight : LatLng): Observable<List<UserLocation>> {
        return signalApi.findNearByPeople(topLeft, bottomRight).attachUserInfo()
    }

    fun findUserLocations(userIds: List<String>, startTime: Long, endTime: Long): Observable<List<UserLocation>> {
        return signalApi.findUserLocations(userIds, startTime, endTime).attachUserInfo()
    }

    private fun Single<List<UserLocation>>.attachUserInfo() : Observable<List<UserLocation>> {
        return flatMapObservable { locations ->
            appComponent.storage.getUsers(locations.mapTo(hashSetOf(), UserLocation::userId))
                    .map { users ->
                        val userMap = users.associateBy(User::id)

                        locations.forEach { it.user = userMap[it.userId] }
                        locations
                    }
        }
    }

    fun inviteRoomMembers(roomId: String): Single<Int> {
        return signalApi.inviteRoomMembers(roomId)
    }

    fun changePassword(oldPassword: String, password: String): Completable {
        return signalApi.changePassword(oldPassword, password)
    }

    fun queryMessages(queries: List<MessageQuery>): Single<List<MessageQueryResult>> {
        return signalApi.queryMessages(queries)
    }

    fun uploadImage(uri: Uri, progressReport: (Int) -> Unit): Single<String> {
        return Single.defer {
            val fileSize = appContext.contentResolver.openInputStream(uri).use {
                val byteArray = ByteArray(4096)

                var rc = it.read(byteArray)
                if (rc <= 0) {
                    return@use rc.toLong()
                }

                var size = 0L
                while (rc > 0) {
                    size += rc
                    rc = it.read(byteArray)
                }
                size
            }

            val reporter: (Long) -> Unit = {
                if (fileSize > 0) {
                    progressReport((it * 90 / fileSize).toInt())
                }
            }

            signalApi.uploadImage(object : RequestBody() {
                override fun contentType(): MediaType {
                    return MediaType.parse("image/jpeg")
                }

                override fun writeTo(sink: BufferedSink) {
                    InputStreamReadReporter(appContext.contentResolver.openInputStream(uri), reporter).use { input ->
                        sink.writeAll(Okio.source(input))
                    }
                }
            })
        }
    }

    companion object {
        const val EXTRA_EVENT = "event"

        private const val CAPTURE_WIDTH = 512
        private const val CAPTURE_HEIGHT = 512
        private const val CAPTURE_RATE = 30
    }
}

