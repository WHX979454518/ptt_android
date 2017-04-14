package com.xianzhitech.ptt.data

import android.content.Context
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.ext.atMost
import com.xianzhitech.ptt.ext.without
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.requery.Persistable
import io.requery.android.sqlite.DatabaseSource
import io.requery.query.Return
import io.requery.reactivex.ReactiveResult
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import java.util.*
import kotlin.collections.LinkedHashSet

class Storage(context: Context,
              val pref: Preference) {

    private val configuration = ConfigurationBuilder(DatabaseSource(context, Models.DEFAULT, 1), Models.DEFAULT).build()
    val store = EntityDataStore<Persistable>(configuration)
    private val data = ReactiveSupport.toReactiveStore(store)

    fun getAllRooms(): Observable<List<Pair<Room, String>>> {
        return data.select(Room::class.java).observeList()
                .switchMap { rooms ->
                    Observable.combineLatest(rooms.map(this::getRoomName)) { names ->
                        List(rooms.size) { index ->
                            rooms[index] to names[index].toString()
                        }
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

    fun getRoomMembers(room: Room, limit: Int? = null): Observable<List<User>> {
        return getGroups(room.groups)
                .switchMap { groups ->
                    val userIds = LinkedHashSet<String>()
                    groups.forEach { userIds.addAll(it.memberIds) }
                    userIds.addAll(room.extraMembers)

                    if (limit == null) {
                        getUsers(userIds)
                    }
                    else {
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
        return Completable.fromObservable(
                data.runInTransaction(
                        data.delete(ContactUser::class.java).get().single(),
                        data.delete(ContactGroup::class.java).get().single(),
                        data.upsert(users),
                        data.upsert(groups)
                )
        )
    }

    fun clearUsersAndGroups(): Completable {
        return Completable.fromObservable(
                data.runInTransaction(
                        data.delete(ContactUser::class.java).get().single(),
                        data.delete(ContactGroup::class.java).get().single()
                )
        )
    }

    fun saveRooms(rooms: Iterable<Room>): Completable {
        return data.upsert(rooms).toCompletable()
    }

    fun saveMessages(messages: Iterable<Message>): Completable {
        return data.upsert(messages).toCompletable()
    }



    private fun <T> Return<ReactiveResult<T>>.observeList(): Observable<List<T>> {
        return get().observableResult().map { it.toList() }
    }
}