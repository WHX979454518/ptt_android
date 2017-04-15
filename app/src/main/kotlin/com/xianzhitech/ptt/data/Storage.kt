package com.xianzhitech.ptt.data

import android.content.Context
import com.google.common.base.Optional
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.atMost
import com.xianzhitech.ptt.ext.toOptional
import com.xianzhitech.ptt.ext.without
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.requery.Persistable
import io.requery.android.sqlite.DatabaseSource
import io.requery.cache.EntityCacheBuilder
import io.requery.query.Return
import io.requery.reactivex.ReactiveResult
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet

class Storage(context: Context,
              val pref: Preference) {

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
                            List(rooms.size) { index ->
                                rooms[index] to (names[index].toString())
                            }
                        }
                    } else {
                        Observable.empty()
                    }
                }
    }

    fun getRoomName(room: Room): Observable<String> {
        if (room.name != null) {
            return Observable.just(room.name)
        }

        val membersWithoutCurrentUser = room.extraMembers.without(pref.currentUser?.id)

        if (room.groups.isNotEmpty() && membersWithoutCurrentUser.isEmpty()) {
            return getGroups(room.groups)
                    .map { it.firstOrNull()?.name ?: "会话名称未知" }
        }

        return getRoomMembers(room, 4)
                .map { users ->
                    val sb = users.atMost(3).joinTo(buffer = StringBuilder(), separator = "、", transform = { it.name })
                    if (users.size == 4) {
                        sb.append("等")
                    }
                    sb.toString()
                }
    }

    fun getRoom(roomId: String): Observable<Optional<Room>> {
        return data.select(Room::class.java)
                .where(RoomType.ID.eq(roomId))
                .observeList()
                .map { it.firstOrNull().toOptional() }
    }

    fun getRoomWithName(roomId: Optional<String>): Observable<Optional<Pair<Room, String>>> {
        if (roomId.isPresent.not()) {
            return Observable.just(Optional.absent())
        }

        val roomObservable = getRoom(roomId.get()).share()

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

    fun getRoomMembers(room: Room, limit: Int? = null): Observable<List<ContactUser>> {
        return getGroups(room.groups)
                .switchMap { groups ->
                    val userIds = LinkedHashSet<String>()
                    groups.forEach { userIds.addAll(it.memberIds) }
                    userIds.addAll(room.extraMembers)

                    if (limit == null) {
                        getUsers(userIds)
                    } else {
                        getUsers(userIds.toList().atMost(limit))
                    }
                }
    }

    fun getAllRoomLatestMessage(): Observable<Map<String, Message>> {
        return Observable.empty()
    }

    fun getAllUsers(): Observable<List<ContactUser>> {
        return data.select(ContactUser::class.java).observeList()
    }

    fun getAllGroups(): Observable<List<ContactGroup>> {
        return data.select(ContactGroup::class.java).observeList()
    }

    fun getGroups(groupIds: Collection<String>): Observable<List<ContactGroup>> {
        return data.select(ContactGroup::class.java).where(ContactGroupType.ID.`in`(groupIds)).observeList()
    }

    fun getUsers(userIds: Collection<String>): Observable<List<ContactUser>> {
        return data.select(ContactUser::class.java).where(ContactUserType.ID.`in`(userIds)).observeList()
    }

    fun getMessagesUpTo(date: Date?,
                        roomId: String,
                        limit: Int): Single<List<Message>> {

        var lookup = data.select(Message::class.java).where(MessageType.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageType.SEND_TIME.lte(date))
        }

        return lookup.limit(limit).observeList().firstOrError()
    }

    fun getMessageFrom(date: Date?,
                       roomId: String): Observable<List<Message>> {
        var lookup = data.select(Message::class.java).where(MessageType.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageType.SEND_TIME.gte(date))
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

    fun clearUsersAndGroups(): Completable {
        return runInTransaction(
                data.delete(ContactUser::class.java).get().single(),
                data.delete(ContactGroup::class.java).get().single()
        )
    }

    fun saveRoom(room: Room): Single<Room> {
        return data.upsert(room).subscribeOn(writeScheduler)
    }

    fun saveMessages(messages: Iterable<Message>): Completable {
        return data.upsert(messages).subscribeOn(writeScheduler).toCompletable()
    }

    private fun runInTransaction(vararg actions: Single<*>): Completable {
        return Completable.fromObservable(data.runInTransaction(*actions).subscribeOn(writeScheduler))
    }

    private fun <T> Return<ReactiveResult<T>>.observeList(): Observable<List<T>> {
        return get().observableResult()
                .subscribeOn(readScheduler)
                .map { it.toList() }
    }
}