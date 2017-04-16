package com.xianzhitech.ptt.broker

import android.content.Context
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.event.*
import com.xianzhitech.ptt.data.*
import com.xianzhitech.ptt.data.exception.NoPermissionException
import com.xianzhitech.ptt.data.exception.NoSuchRoomException
import com.xianzhitech.ptt.data.exception.ServerException
import com.xianzhitech.ptt.ext.*
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
                   appContext: Context) {
    private val logger = LoggerFactory.getLogger("SignalBroker")

    private val signalApi = SignalApi(appComponent, appContext)

    val events = signalApi.events
    val connectionState = signalApi.connectionState
    val currentUser = signalApi.currentUser

    val currentVideoRoomId: BehaviorSubject<Optional<String>> = BehaviorSubject.createDefault(Optional.absent<String>())
    val currentWalkieRoom : BehaviorSubject<Optional<CurrentWalkieRoom>> = BehaviorSubject.createDefault(Optional.absent())

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
                .subscribe(this::onSignalEvent)
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
        if (currentVideoRoomId.value.isPresent) {
            quitVideoRoom()
        }

        if (currentWalkieRoom.value.isPresent) {
            quitWalkieRoom()
        }

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

    fun getRoomPermissionException(room: Room): NoPermissionException? {
        val user = currentUser.value.orNull() ?: return null

        if (room.extraMemberIds.without(user.id).isNotEmpty()) {
            if (room.extraMemberIds.size <= 2) {
                return if (user.hasPermission(Permission.CALL_INDIVIDUAL)) {
                    NoPermissionException(Permission.CALL_INDIVIDUAL)
                } else {
                    null
                }
            }

            return if (user.hasPermission(Permission.CALL_TEMP_GROUP)) {
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

    private fun onSignalEvent(event: Event) {
        logger.i { "on signal event: $event" }

        when (event) {
            is WalkieRoomActiveInfoUpdateEvent -> {
                val currentRoom = currentWalkieRoom.value.orNull()
                if (currentRoom?.roomId == event.roomId) {
                    currentWalkieRoom.onNext(currentRoom.copy(
                            currentSpeakerId = event.speakerId,
                            currentSpeakerPriority = event.speakerPriority,
                            onlineMemberIds = event.onlineMemberIds
                    ).toOptional())
                }
            }

            is WalkieRoomSpeakerUpdateEvent -> {
                val currentRoom = currentWalkieRoom.value.orNull()
                if (currentRoom?.roomId == event.roomId) {
                    currentWalkieRoom.onNext(currentRoom.copy(
                            currentSpeakerId = event.speakerId,
                            currentSpeakerPriority = event.speakerPriority
                    ).toOptional())
                }
            }

            is CurrentUser -> {
                currentUser.onNext(event.toOptional())
            }

            is Room -> {
                val currentRoom = currentWalkieRoom.value.orNull()
                if (currentRoom?.roomId == event.id) {
                    currentWalkieRoom.onNext(currentRoom.copy(room = event).toOptional())
                }

                appComponent.storage.saveRoom(event)
                        .toMaybe()
                        .logErrorAndForget()
                        .subscribe()
            }
        }
    }


    fun joinWalkieRoom(roomId: String, fromInvitation: Boolean) {
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

        currentVideoRoomId.onNext(roomId.toOptional())

        joinWalkieDisposable = appComponent.storage.getRoom(roomId)
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
                .flatMap { response ->
                    appComponent.storage.saveRoom(response.room).map { response }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (room, initiatorUserId, onlineMemberIds, speakerId, speakerPriority) ->
                    currentWalkieRoom.onNext(CurrentWalkieRoom(
                            roomId = roomId,
                            room = room,
                            initiatorUserId = initiatorUserId,
                            onlineMemberIds = onlineMemberIds,
                            currentSpeakerId = speakerId,
                            currentSpeakerPriority = speakerPriority
                    ).toOptional())
                }
    }

    fun quitWalkieRoom() {
        joinWalkieDisposable?.dispose()
        joinWalkieDisposable = null

        val walkieRoomId = peekWalkieRoomId()

        currentWalkieRoom.onNext(Optional.absent())

        if (walkieRoomId != null) {
            signalApi.leaveWalkieRoom(walkieRoomId)
                    .logErrorAndForget()
                    .subscribe()
        }
    }

    fun joinVideoRoom(roomId: String, audioOnly: Boolean): Completable {
        return Completable.complete()
    }

    fun quitVideoRoom() {
    }

    private fun peekUserId(): String? = currentUser.value.orNull()?.id
    private fun peekVideoRoomId(): String? = currentVideoRoomId.value.orNull()
    private fun peekWalkieRoomId(): String? = currentWalkieRoom.value.orNull()?.roomId

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
}

