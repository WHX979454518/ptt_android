package com.xianzhitech.ptt.broker

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import com.baidu.mapapi.model.LatLngBounds
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.dto.NearbyUser
import com.xianzhitech.ptt.api.event.*
import com.xianzhitech.ptt.data.CurrentUser
import com.xianzhitech.ptt.data.Location
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageEntity
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.NoPermissionException
import com.xianzhitech.ptt.service.NoSuchRoomException
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.toast
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit


class SignalBroker(private val appComponent: AppComponent,
                   private val appContext: Context) {
    private val logger = LoggerFactory.getLogger("SignalBroker")

    private val signalApi = SignalApi(appComponent, appContext)

    val events : Observable<Event>
    get() = signalApi.events.map { it.second }

    val connectionState = signalApi.connectionState
    val currentUser = signalApi.currentUser

    val currentVideoRoomId: BehaviorSubject<Optional<String>> = BehaviorSubject.createDefault(Optional.absent<String>())
    val currentWalkieRoomState: BehaviorSubject<RoomState> = BehaviorSubject.createDefault(RoomState.EMPTY)

    val currentWalkieRoomId : Observable<Optional<String>>
    get() = currentWalkieRoomState.map { it.currentRoomId.toOptional() }.distinctUntilChanged()


    private var joinWalkieDisposable: Disposable? = null

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
                            speakerPriority = event.speakerPriority,
                            onlineMemberIds = event.onlineMemberIds
                    ))
                }
            }

            is WalkieRoomSpeakerUpdateEvent -> {
                val state = currentWalkieRoomState.value
                if (state.currentRoomId == event.roomId) {
                    currentWalkieRoomState.onNext(state.copy(
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

        val intent = Intent(action).setPackage(appContext.packageName)
        @Suppress("USELESS_CAST")
        when (event) {
            is Parcelable -> intent.putExtra(EXTRA_EVENT, event as Parcelable)
            else -> intent.putExtra(EXTRA_EVENT, event)
        }
        appContext.sendBroadcast(intent)
    }


    fun joinWalkieRoom(roomId: String, fromInvitation: Boolean) : Completable {
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

                            currentWalkieRoomState.onNext(currState.copy(
                                    status = if (peekUserId() == speakerId) RoomStatus.ACTIVE else RoomStatus.JOINED,
                                    speakerId = speakerId,
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

    fun quitWalkieRoom(askOthersToLeave : Boolean = appComponent.preference.keepSession.not()) {
        joinWalkieDisposable?.dispose()
        joinWalkieDisposable = null

        val walkieRoomId = peekWalkieRoomId()

        currentWalkieRoomState.onNext(RoomState.EMPTY)

        if (walkieRoomId != null) {
            recordWalkieRoomActive(walkieRoomId)
            signalApi.leaveWalkieRoom(walkieRoomId, askOthersToLeave)
                    .logErrorAndForget()
                    .subscribe()
        }
    }

    fun grabWalkieMic() : Single<Boolean> {
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
                                currentWalkieRoomState.onNext(newRoomState.copy(
                                        speakerId = currentUser.id,
                                        speakerPriority = currentUser.priority,
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
                signalApi.releaseWalkieMic(roomState.currentRoomId)
            } else {
                Completable.complete()
            }
        }.subscribeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe()
    }

    fun joinVideoRoom(roomId: String, audioOnly: Boolean): Completable {
        return Completable.complete()
    }

    fun quitVideoRoom() {
    }

    fun peekUserId(): String? = currentUser.value.orNull()?.id
    private fun peekVideoRoomId(): String? = currentVideoRoomId.value.orNull()
    fun peekWalkieRoomId(): String? = currentWalkieRoomState.value.currentRoomId

    fun sendMessage(message: Message): Single<Message> {
        return appComponent.storage.saveMessage(message)
                .flatMap(signalApi::sendMessage)
                .flatMap(appComponent.storage::saveMessage)
    }

    fun createMessage(roomId: String, type: MessageType, body: Any? = null): Message {
        val entity = MessageEntity()
        entity.setSendTime(Date())
        entity.setLocalId(UUID.randomUUID().toString())
        entity.setRoomId(roomId)
        entity.setSenderId(currentUser.value.get().id)
        entity.setType(type)

        if (body != null && type.bodyClass != null) {
            Preconditions.checkArgument(type.bodyClass.isAssignableFrom(body.javaClass))
            entity.setBody(appComponent.objectMapper.writeValueAsString(body))
        }

        return entity
    }

    fun createRoom(userIds: List<String> = emptyList(),
                   groupIds: List<String> = emptyList()): Single<Room> {
        return signalApi.createRoom(userIds, groupIds)
                .flatMap(appComponent.storage::saveRoom)
                .doOnSuccess { room ->
                    sendMessage(createMessage(room.id, MessageType.NOTIFY_CREATE_ROOM)).toMaybe().logErrorAndForget().subscribe()
                }
    }

    fun sendLocationData(locations: List<Location>): Completable {
        return signalApi.sendLocationData(locations)
    }

    fun quitGroupChat() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun updateRoom(roomId: String): Single<Room> {
        return signalApi.getRoom(roomId)
                .flatMap(appComponent.storage::saveRoom)
    }

    fun addRoomMembers(roomId: String, newMemberIds: List<String>): Single<Room> {
        return signalApi.addRoomMembers(roomId, newMemberIds)
                .flatMap(appComponent.storage::saveRoom)
    }

    fun searchNearbyUsers(bounds: LatLngBounds): Single<List<NearbyUser>> {
        TODO("not implemented")
    }

    fun inviteRoomMembers(roomId: String): Single<Int> {
        return signalApi.inviteRoomMembers(roomId)
    }

    fun changePassword(oldPassword: String, password: String): Completable {
        return signalApi.changePassword(oldPassword, password)
    }

    companion object {
        const val EXTRA_EVENT = "event"
    }
}

