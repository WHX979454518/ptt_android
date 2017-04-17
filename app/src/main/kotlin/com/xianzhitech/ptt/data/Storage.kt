package com.xianzhitech.ptt.data

import android.content.Context
import com.google.common.base.Optional
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import io.requery.Persistable
import io.requery.android.sqlite.DatabaseSource
import io.requery.cache.EntityCacheBuilder
import io.requery.query.Return
import io.requery.reactivex.ReactiveResult
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet

class Storage(context: Context,
              val appComponent: AppComponent) {

    private val pref : Preference
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

    fun getAllRooms(): Observable<List<Pair<Room, String>>> {
        return data.select(Room::class.java).observeList()
                .switchMap { rooms ->
                    if (rooms.isNotEmpty()) {
                        Observable.combineLatest(rooms.map(this::getRoomName)) { names ->
                            names.mapIndexed { index, name -> rooms[index] to (name as String) }
                        }
                    } else {
                        Observable.empty()
                    }
                }
    }

    fun getRoomName(room: Room): Observable<String> {
        return Observable.defer {
            if (room.name != null) {
                return@defer Observable.just(room.name)
            }

            val membersWithoutCurrentUser = room.extraMemberIds.without(pref.currentUser?.id)

            if (room.groupIds.isNotEmpty() && membersWithoutCurrentUser.isEmpty()) {
                return@defer getGroups(room.groupIds)
                        .map { it.firstOrNull()?.name ?: "会话名称未知" }
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

    fun getRoomName(roomId: String) : Observable<String> {
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
                        Observable.empty()
                    }
                },
                BiFunction { room, name -> room.transform { it!! to name } }
        )
    }

    fun getRoomWithInfo(roomId: String) : Observable<Optional<Pair<Room, RoomInfo?>>> {
        return Observable.combineLatest(
                getRoom(roomId),
                getRoomInfo(roomId),
                BiFunction { room, info -> room.transform { it!! to info.orNull() } }
        )
    }

    fun getRoomInfo(roomId: String) : Observable<Optional<RoomInfo>> {
        return data.select(RoomInfo::class.java)
                .where(RoomInfoEntity.ROOM_ID.eq(roomId))
                .observeList()
                .map { it.firstOrNull().toOptional() }
    }

    fun updateRoomLastWalkieActiveTime(roomId : String,
                                       date: Date = Date()) : Completable {
        return data.update(RoomInfo::class.java)
                .set(RoomInfoEntity.LAST_WALKIE_ACTIVE_TIME, date)
                .where(RoomInfoEntity.ROOM_ID.eq(roomId))
                .get()
                .single()
                .subscribeOn(writeScheduler)
                .toCompletable()
    }

    fun getRoomMembers(room: Room, limit: Int? = null, includeSelf : Boolean = true): Observable<List<User>> {
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

    fun getRoomMembers(roomId : String, limit: Int? = null) : Observable<List<User>> {
        return getRoom(roomId)
                .switchMap { room ->
                    if (room.isPresent) {
                        getRoomMembers(room.get(), limit)
                    } else {
                        Observable.just(emptyList())
                    }
                }
    }

    fun getRoomDetails(roomId: String, memberLimit : Int? = null) : Observable<Optional<RoomDetails>> {
        val room = getRoom(roomId).share()
        return Observable.combineLatest(
                room,
                room.switchMap {
                    if (it.isPresent) {
                        getRoomName(it.get())
                    } else {
                        Observable.empty()
                    }
                },
                room.switchMap {
                    if (it.isPresent) {
                        getRoomMembers(it.get(), memberLimit)
                    } else {
                        Observable.empty()
                    }
                },
                Function3 { room, name, members -> room.transform { RoomDetails(it!!, name, members) } }
        )
    }

    fun getAllRoomLatestMessage(): Observable<Map<String, Message>> {
        //TODO:
        return Observable.empty()
    }

    fun getAllRoomInfo() : Observable<Map<String, RoomInfo>> {
        return data.select(RoomInfo::class.java)
                .get()
                .observableResult()
                .subscribeOn(readScheduler)
                .map {
                    it.toMap(RoomInfoEntity.ROOM_ID)
                }
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

    fun getGroup(groupId : String) : Observable<Optional<ContactGroup>> {
        return getGroups(listOf(groupId))
                .map { it.firstOrNull().toOptional() }
    }

    fun getUsers(userIds: Collection<String>): Observable<List<User>> {
        return data.select(ContactUser::class.java)
                .where(ContactUserEntity.ID.`in`(userIds))
                .observeList()
                .map {
                    val currentUser = appComponent.signalBroker.currentUser.value.orNull()
                    if (currentUser != null && userIds.contains(currentUser.id)) {
                        it + currentUser
                    } else {
                        it
                    }
                }
    }

    fun getUser(userId: String) : Observable<Optional<User>> {
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

    fun getMessagesFrom(date: Date?, roomId: String): Observable<List<Message>> {
        var lookup = data.select(Message::class.java).where(MessageEntity.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageEntity.SEND_TIME.gte(date))
        }

        return lookup.observeList()
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
                data.delete(Message::class.java).get().single()
        )
    }

    fun saveRoom(room: Room): Single<Room> {
        return data.upsert(room).subscribeOn(writeScheduler)
    }

    fun saveMessages(messages: Iterable<Message>): Completable {
        return data.upsert(messages).subscribeOn(writeScheduler).toCompletable()
    }

    fun saveMessage(message : Message) : Single<Message> {
        return data.upsert(message).subscribeOn(writeScheduler)
    }

    fun removeRoom(roomId: String) : Completable {
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