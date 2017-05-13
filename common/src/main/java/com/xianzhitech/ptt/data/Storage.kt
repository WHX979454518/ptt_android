package com.xianzhitech.ptt.data

import android.content.Context
import android.support.v4.util.ArrayMap
import com.google.common.base.Optional
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.*
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import io.requery.Column
import io.requery.Entity
import io.requery.Persistable
import io.requery.Table
import io.requery.android.sqlite.DatabaseSource
import io.requery.cache.EntityCacheBuilder
import io.requery.query.Return
import io.requery.query.function.Count
import io.requery.reactivex.ReactiveResult
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.stream.Collectors.toList
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class Storage(context: Context,
              val appComponent: AppComponent) {

    private val pref: Preference
        get() = appComponent.preference

    private val logger = LoggerFactory.getLogger(javaClass)

    private val configuration = ConfigurationBuilder(DatabaseSource(context, Models.DEFAULT, 1), Models.DEFAULT).let {
        it.setEntityCache(EntityCacheBuilder(Models.DEFAULT).useReferenceCache(true).build())
        it.build()
    }
    val store = EntityDataStore<Persistable>(configuration)
    private val data = ReactiveSupport.toReactiveStore(store)

    private val readScheduler = Schedulers.from(Executors.newSingleThreadExecutor())
    private val writeScheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Suppress("UNCHECKED_CAST")
    fun getAllRooms(maxRoomMember: Int = 9): Observable<List<RoomWithMembersAndName>> {
        return data.select(Room::class.java)
                .observeList()
                .switchMap { rooms ->
                    if (rooms.isNotEmpty()) {
                        combineLatest(
                                Observable.combineLatest(rooms.map(this::getRoomName)) { it },
                                Observable.combineLatest(rooms.map { getRoomMembers(it, maxRoomMember) }) { it },
                                { roomNames, roomMembers ->
                                    rooms.mapIndexedNotNull { index, room ->
                                        RoomWithMembersAndName(room = room, members = (roomMembers[index] as List<User>), name = roomNames[index].toString())
                                    }
                                }
                        )
                    } else {
                        Observable.just(emptyList())
                    }
                }
    }

    fun getRoomName(room: Room): Observable<String> {
        return Observable.defer {
            if (room.name.isNullOrBlank().not()) {
                return@defer Observable.just(room.name)
            }

            val membersWithoutCurrentUser = room.extraMemberIds.without(pref.currentUser?.id)

            if (room.groupIds.isNotEmpty() && membersWithoutCurrentUser.isEmpty()) {
                return@defer getGroups(room.groupIds)
                        .map { it.firstOrNull()?.name ?: "" }
            }

            getRoomMembers(room, 4, includeSelf = false)
                    .map { users ->
                        val sb = users.atMost(3).joinTo(buffer = StringBuilder(), separator = "、", transform = User::name)
                        if (users.size == 4) {
                            sb.append("等")
                        }
                        sb.toString()
                    }
        }
    }

    fun getRoomName(roomId: String): Observable<String> {
        return getRoom(roomId)
                .switchMap { room ->
                    if (room.isPresent) {
                        getRoomName(room.get())
                    } else {
                        Observable.just("")
                    }
                }
    }

    fun getRoom(roomId: String): Observable<Optional<Room>> {
        return data.select(Room::class.java)
                .where(RoomEntity.ID.eq(roomId))
                .observeList()
                .map { it.firstOrNull().toOptional() }
    }

    fun getRoomWithName(roomId: String): Observable<Optional<Pair<Room, String>>> {
        val roomObservable = getRoom(roomId).share()

        return Observable.combineLatest(
                roomObservable,
                roomObservable.switchMap { room ->
                    if (room.isPresent) {
                        getRoomName(room.get())
                    } else {
                        Observable.just("")
                    }
                },
                BiFunction { room, name -> room.transform { it!! to name } }
        )
    }

    fun getRoomWithInfo(roomId: String): Observable<Optional<Pair<Room, RoomInfo?>>> {
        return Observable.combineLatest(
                getRoom(roomId),
                getRoomInfo(roomId),
                BiFunction { room, info -> room.transform { it!! to info.orNull() } }
        )
    }

    fun getRoomInfo(roomId: String): Observable<Optional<RoomInfo>> {
        return data.select(RoomInfo::class.java)
                .where(RoomInfoEntity.ROOM_ID.eq(roomId))
                .observeList()
                .map { it.firstOrNull().toOptional() }
    }

    fun updateRoomLastWalkieActiveTime(roomId: String,
                                       date: Date = Date()): Completable {
        return data.update(RoomInfo::class.java)
                .set(RoomInfoEntity.LAST_WALKIE_ACTIVE_TIME, date)
                .where(RoomInfoEntity.ROOM_ID.eq(roomId))
                .get()
                .single()
                .subscribeOn(writeScheduler)
                .toCompletable()
    }

    fun getRoomMembers(room: Room, limit: Int? = null, includeSelf: Boolean = true): Observable<List<User>> {
        return getGroups(room.groupIds)
                .switchMap { groups ->
                    val userIds = LinkedHashSet<String>()
                    groups.flatMapTo(userIds, ContactGroup::memberIds)
                    userIds.addAll(room.extraMemberIds)

                    if (includeSelf.not()) {
                        userIds.remove(appComponent.preference.currentUser?.id)
                    }

                    if (limit == null) {
                        getUsers(userIds)
                    } else {
                        getUsers(userIds.keepAtMost(limit))
                    }
                }
    }

    fun getRoomMemberNumber(room: Room): Observable<Int> {
        return getGroups(room.groupIds)
                .map {
                    it.fold(room.extraMemberIds.toMutableSet()) { set, group ->
                        set.addAll(group.memberIds)
                        set
                    }.size
                }
    }

    fun getRoomMembers(roomId: String, limit: Int? = null, includeSelf: Boolean = true): Observable<List<User>> {
        return getRoom(roomId)
                .switchMap { room ->
                    if (room.isPresent) {
                        getRoomMembers(room.get(), limit, includeSelf)
                    } else {
                        Observable.just(emptyList())
                    }
                }
    }

    fun getRoomDetails(roomId: String, memberLimit: Int? = null, includeSelf: Boolean = true): Observable<Optional<RoomDetails>> {
        val room = getRoom(roomId).share()
        return Observable.combineLatest(
                room,
                room.switchMap {
                    if (it.isPresent) {
                        getRoomName(it.get())
                    } else {
                        Observable.just("")
                    }
                },
                room.switchMap {
                    if (it.isPresent) {
                        getRoomMembers(it.get(), memberLimit, includeSelf)
                    } else {
                        Observable.just(emptyList())
                    }
                },
                Function3 { room, name, members -> room.transform { RoomDetails(it!!, name, members) } }
        )
    }

    fun getRooms(roomIds: Collection<String>): Observable<List<Room>> {
        return data.select(Room::class.java)
                .where(RoomEntity.ID.`in`(roomIds))
                .observeList()
    }

    fun getAllRoomIds(): Observable<List<String>> {
        return data.select(RoomEntity.ID)
                .observeList()
                .map { it.map { it[RoomEntity.ID] } }
    }

    fun getLatestUnreadMessages(limit: Int): Observable<List<MessageWithRoomNameAndSender>> {
        val currentUserId = appComponent.signalBroker.peekUserId() ?: return Observable.just(emptyList())

        return data.select(Message::class.java)
                .where(MessageEntity.HAS_READ.eq(false))
                .and(MessageEntity.SENDER_ID.ne(currentUserId))
                .and(MessageEntity.TYPE.`in`(MessageType.MEANINGFUL))
                .orderBy(MessageEntity.SEND_TIME.desc())
                .limit(limit)
                .observeList()
                .switchMap { messages ->
                    combineLatest(
                            Observable.combineLatest(messages.map { getRoomName(it.roomId) }, { it }),
                            getUsers(messages.map(Message::senderId)),
                            { roomNames, senders ->
                                val senderMap = senders.associateBy(User::id)
                                messages.mapIndexedNotNull { index, message ->
                                    val roomName = roomNames[index].toString()
                                    if (roomName.isBlank()) {
                                        null
                                    } else {
                                        MessageWithRoomNameAndSender(message = message, roomName = roomName, sender = senderMap[message.senderId])
                                    }
                                }
                            }
                    )
                }
    }

    fun getAllRoomLatestMessage(): Observable<Map<String, MessageWithSender>> {
        return data.select(MessageEntity.ROOM_ID)
                .distinct()
                .observeList()
                .switchMap {
                    Observable.combineLatest(
                            it.map { getLatestMessage(it[0]) },
                            {
                                val map = ArrayMap<String, MessageWithSender>(it.size)
                                it.forEach {
                                    @Suppress("UNCHECKED_CAST")
                                    val msg = it as Optional<MessageWithSender>
                                    if (msg.isPresent) {
                                        map[msg.get().message.roomId] = msg.get()
                                    }
                                }

                                map
                            }
                    )
                }
    }

    fun getAllRoomUnreadMessageCount(): Observable<Map<String, Int>> {
        val currentUserId = appComponent.signalBroker.peekUserId() ?: return Observable.just(emptyMap())

        return data.select(MessageEntity.ROOM_ID, Count.count(Message::class.java))
                .where(MessageEntity.HAS_READ.eq(false))
                .and(MessageEntity.SENDER_ID.ne(currentUserId))
                .and(MessageEntity.TYPE.`in`(MessageType.MEANINGFUL))
                .groupBy(MessageEntity.ROOM_ID)
                .get()
                .observableResult()
                .subscribeOn(readScheduler)
                .map {
                    it.associateByTo(ArrayMap<String, Int>(it.count()), { it[0] }, { it[1] })
                }
    }

    fun getLatestMessage(roomId: String): Observable<Optional<MessageWithSender>> {
        return data.select(Message::class.java)
                .where(MessageEntity.ROOM_ID.eq(roomId))
                .and(MessageEntity.TYPE.`in`(MessageType.MEANINGFUL))
                .orderBy(MessageEntity.SEND_TIME.desc())
                .limit(1)
                .observeList()
                .switchMap { msgs ->
                    val msg = msgs.firstOrNull() ?: return@switchMap Observable.just(Optional.absent<MessageWithSender>())

                    getUser(msg.senderId)
                            .map {
                                MessageWithSender(msg, it.orNull()).toOptional()
                            }
                }
    }

    fun getAllRoomInfo(): Observable<List<RoomInfo>> {
        return data.select(RoomInfo::class.java)
                .observeList()
    }

    fun getAllUsers(): Observable<List<ContactUser>> {
        return data.select(ContactUser::class.java).observeList()
    }

    fun getAllGroups(): Observable<List<ContactGroup>> {
        return data.select(ContactGroup::class.java).observeList()
    }

    fun getGroups(groupIds: Collection<String>): Observable<List<ContactGroup>> {
        return data.select(ContactGroup::class.java).where(ContactGroupEntity.ID.`in`(groupIds)).observeList()
    }

    fun getGroup(groupId: String): Observable<Optional<ContactGroup>> {
        return getGroups(listOf(groupId))
                .map { it.firstOrNull().toOptional() }
    }

    fun getUsers(userIds: Collection<String>): Observable<List<User>> {
        return data.select(ContactUser::class.java)
                .where(ContactUserEntity.ID.`in`(userIds))
                .observeList()
                .map {
                    val currentUser = appComponent.signalBroker.currentUser.value.orNull()
                    @Suppress("UNCHECKED_CAST")
                    if (currentUser != null && userIds.contains(currentUser.id)) {
                        (it + currentUser) as List<User>
                    } else {
                        it
                    }
                }
    }


    fun getUser(userId: String): Observable<Optional<User>> {
        return getUsers(listOf(userId))
                .map { it.firstOrNull().toOptional() }
    }

    fun getMessagesUpTo(date: Date?,
                        roomId: String,
                        limit: Int): Single<List<Message>> {

        var lookup = data.select(Message::class.java).where(MessageEntity.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageEntity.SEND_TIME.lte(date))
        }

        return lookup.limit(limit).observeList().firstOrError()
    }

    fun getMessagesFrom(date: Date?, roomId: String): Observable<List<MessageWithSender>> {
        var messageLookup = data.select(Message::class.java)
                .where(MessageEntity.ROOM_ID.eq(roomId))

        var messageSenderIdLookup = data.select(MessageEntity.SENDER_ID)
                .where(MessageEntity.ROOM_ID.eq(roomId))

        if (date != null) {
            messageLookup = messageLookup.and(MessageEntity.SEND_TIME.gte(date))
            messageSenderIdLookup = messageSenderIdLookup.and(MessageEntity.SEND_TIME.gte(date))
        }

        return combineLatest(
                messageLookup.observeList(),
                data.select(ContactUser::class.java)
                        .where(ContactUserEntity.ID.`in`(messageSenderIdLookup))
                        .get()
                        .observableResult()
                        .subscribeOn(readScheduler)
                        .map { it.associateByTo(ArrayMap(it.count()), ContactUser::id) },
                { messages, userMap ->
                    val currentUserId = appComponent.signalBroker.peekUserId()
                    messages.map {
                        MessageWithSender(it, if (it.senderId == currentUserId) appComponent.signalBroker.currentUser.value.orNull() else userMap[it.senderId])
                    }
                }
        )
    }

    fun markRoomAllMessagesRead(roomId: String): Completable {
        return data.count(Message::class.java)
                .where(MessageEntity.HAS_READ.eq(false))
                .and(MessageEntity.ROOM_ID.eq(roomId))
                .get()
                .single()
                .subscribeOn(readScheduler)
                .flatMapCompletable {
                    if (it > 0) {
                        data.update(Message::class.java)
                                .set(MessageEntity.HAS_READ, true)
                                .where(MessageEntity.HAS_READ.eq(false))
                                .and(MessageEntity.ROOM_ID.eq(roomId))
                                .get()
                                .single()
                                .subscribeOn(writeScheduler)
                                .toCompletable()
                    } else {
                        Completable.complete()
                    }
                }
    }

    fun replaceAllUsersAndGroups(users: Iterable<ContactUser>, groups: Iterable<ContactGroup>): Completable {
        return runInTransaction(
                data.delete(ContactUser::class.java).get().single(),
                data.delete(ContactGroup::class.java).get().single(),
                data.upsert(users),
                data.upsert(groups)
        )
    }

    fun clear(): Completable {
        return runInTransaction(
                data.delete(ContactUser::class.java).get().single(),
                data.delete(ContactGroup::class.java).get().single(),
                data.delete(RoomInfo::class.java).get().single(),
                data.delete(Room::class.java).get().single(),
                data.delete(Message::class.java).get().single(),
                data.delete(PendingLocation::class.java).get().single()
        )
    }

    fun saveRoom(room: Room): Single<Room> {
        return data.upsert(room).subscribeOn(writeScheduler)
    }

    fun saveMessages(messages: Iterable<Message>): Completable {
        return data.upsert(messages).subscribeOn(writeScheduler).toCompletable()
    }

    fun saveMessage(message: Message): Single<Message> {
        return data.upsert(message).subscribeOn(writeScheduler)
    }

    fun setMessageError(localMessageId: String, error: Boolean): Completable {
        return data.update(Message::class.java)
                .set(MessageEntity.ERROR, error)
                .where(MessageEntity.LOCAL_ID.eq(localMessageId))
                .and(MessageEntity.REMOTE_ID.isNull())
                .get()
                .single()
                .subscribeOn(writeScheduler)
                .toCompletable()
    }

    fun removeRoom(roomId: String): Completable {
        return data.delete(Room::class.java)
                .where(RoomEntity.ID.eq(roomId))
                .get()
                .single()
                .subscribeOn(writeScheduler)
                .toCompletable()
    }

    fun updateRoomName(roomId: String, roomName: String?): Completable {
        return data.update(Room::class.java)
                .set(RoomEntity.NAME, roomName)
                .where(RoomEntity.ID.eq(roomId))
                .get()
                .single()
                .subscribeOn(writeScheduler)
                .toCompletable()
    }

    fun savePendingLocations(locations: Collection<Location>): Completable {
        if (locations.isEmpty()) {
            return Completable.complete()
        }

        logger.i { "Saving ${locations.size} locations to database" }

        return Completable.defer {
            val pendingLocations = locations.map {
                PendingLocationEntity().apply {
                    setLocationText(appComponent.objectMapper.writeValueAsString(it))
                }
            }

            data.upsert(pendingLocations).toCompletable()
        }.subscribeOn(writeScheduler)
    }

    fun getPendingLocations(): Observable<List<Location>> {
        return data.select(PendingLocation::class.java)
                .get()
                .observableResult()
                .subscribeOn(readScheduler)
                .map { result ->
                    result.map {
                        appComponent.objectMapper.readValue(it.locationText, Location::class.java)
                    }
                }
    }

    fun removePendingLocations(locations: Collection<Location>): Completable {
        if (locations.isEmpty()) {
            return Completable.complete()
        }

        return Completable.defer {
            data.delete(PendingLocation::class.java)
                    .where(PendingLocationEntity.LOCATION_TEXT.`in`(locations.map(appComponent.objectMapper::writeValueAsString)))
                    .get()
                    .single()
                    .toCompletable()
        }.subscribeOn(writeScheduler)
    }

    private fun runInTransaction(vararg actions: Single<*>): Completable {
        return Completable.fromObservable(data.runInTransaction(*actions).subscribeOn(writeScheduler))
    }

    private fun <T> Return<ReactiveResult<T>>.observeList(): Observable<List<T>> {
        return get().observableResult()
                .subscribeOn(readScheduler)
                .doOnError { logger.e(it) { "Error executing storage query: " } }
                .map { it.toList() }
    }
}

@Entity
@Table(name = "pending_locations")
interface PendingLocation : Persistable {
    @get:Column(name = "location_text", unique = true)
    val locationText: String
}

data class MessageWithSender(val message: Message,
                             val user: User?)

data class RoomWithMembersAndName(val room: Room,
                                  val members: List<User>,
                                  val name: String)

data class MessageWithRoomNameAndSender(val message: Message,
                                        val roomName: String?,
                                        val sender: User?)