package com.xianzhitech.ptt.data

import android.content.Context
import com.xianzhitech.ptt.ext.subscribeSimple
import io.requery.Persistable
import io.requery.android.sqlite.DatabaseSource
import io.requery.query.Return
import io.requery.rx.RxResult
import io.requery.rx.RxSupport
import io.requery.sql.ConfigurationBuilder
import io.requery.sql.EntityDataStore
import rx.Observable
import rx.Single
import java.util.*


class Storage(context: Context) {
    private val configuration = ConfigurationBuilder(DatabaseSource(context, Models.DEFAULT, 1), Models.DEFAULT).build()
    val store = EntityDataStore<Persistable>(configuration)
    private val data = RxSupport.toReactiveStore(store)

    fun getAllRooms(): Observable<List<Room>> {
        return data.select(Room::class.java).observeList()
    }

    fun getAllRoomLatestMessage() : Observable<Pair<String, Message>> {
        return Observable.empty()
    }

    fun getAllUsers(): Observable<List<User>> {
        return data.select(User::class.java).observeList()
    }

    fun getAllGroups(): Observable<List<Group>> {
        return data.select(Group::class.java).observeList()
    }

    fun getMessagesUpTo(date: Date?,
                        roomId: String,
                        limit: Int): Single<List<Message>> {

        var lookup = data.select(Message::class.java).where(MessageEntity.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageEntity.SEND_TIME.lte(date))
        }

        return lookup.limit(limit).observeList().first().toSingle()
    }

    fun getMessageFrom(date: Date?,
                       roomId: String): Observable<List<Message>> {
        var lookup = data.select(Message::class.java).where(MessageEntity.ROOM_ID.eq(roomId))
        if (date != null) {
            lookup = lookup.and(MessageEntity.SEND_TIME.gte(date))
        }

        return lookup.observeList()
    }

    fun saveUsersAndGroups(users: Iterable<User>, groups: Iterable<Group>) {
        data.runInTransaction(data.upsert(users), data.upsert(groups)).subscribeSimple()
    }

    fun saveRooms(rooms: Iterable<Room>) {
        data.upsert(rooms).subscribeSimple()
    }

    fun saveMessages(messages: Iterable<Message>) {
        data.upsert(messages).subscribeSimple()
    }

    private fun <T> Return<RxResult<T>>.observeList(): Observable<List<T>> {
        return get().toSelfObservable().map { it.toList() }
    }
}