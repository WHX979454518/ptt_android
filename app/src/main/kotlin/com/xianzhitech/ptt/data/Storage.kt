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

            getRoomMembers(room, 4)
                    .map { users ->
                        val sb = users.atMost(3).joinTo(buffer = StringBuilder(), separator = "、", transform = ContactUser::name)
                        if (users.size == 4) {
                            sb.append("等")
                        }
                        sb.toString()
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

    fun getRoomMembers(room: Room, limit: Int? = null): Observable<List<ContactUser>> {
        return getGroups(room.groupIds)
                .switchMap { groups ->
                    val userIds = LinkedHashSet<String>()
                    groups.flatMapTo(userIds, ContactGroup::memberIds)
                    userIds.addAll(room.extraMemberIds)

                    if (limit == null) {
                        getUsers(userIds)
                    } else {
                        getUsers(userIds.keepAtMost(limit))
                    }
                }
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

    fun getUsers(userIds: Collection<String>): Observable<List<ContactUser>> {
        return data.select(ContactUser::class.java).where(ContactUserEntity.ID.`in`(userIds)).observeList()
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