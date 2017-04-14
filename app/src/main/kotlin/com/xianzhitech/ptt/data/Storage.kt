package com.xianzhitech.ptt.data

import android.content.Context
import com.xianzhitech.ptt.ext.logErrorAndForget
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
import rx.lang.kotlin.toSingle
import java.util.*

class Storage(context: Context) {
    private val configuration = ConfigurationBuilder(DatabaseSource(context, Models.DEFAULT, 1), Models.DEFAULT).build()
    val store = EntityDataStore<Persistable>(configuration)
    private val data = ReactiveSupport.toReactiveStore(store)

    fun getAllRooms(): Observable<List<Room>> {
        return data.select(Room::class.java).observeList()
    }

    fun getAllRoomLatestMessage(): Observable<Pair<String, Message>> {
        return Observable.empty()
    }

    fun getAllUsers(): Observable<List<ContactUser>> {
        return data.select(ContactUser::class.java).observeList()
    }

    fun getAllGroups(): Observable<List<ContactGroup>> {
        return data.select(ContactGroup::class.java).observeList()
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

    fun clearUsersAndGroups() : Completable {
        return Completable.fromObservable(
                data.runInTransaction(
                        data.delete(ContactUser::class.java).get().single(),
                        data.delete(ContactGroup::class.java).get().single()
                )
        )
    }

    fun saveRooms(rooms: Iterable<Room>) : Completable {
        return data.upsert(rooms).toCompletable()
    }

    fun saveMessages(messages: Iterable<Message>) : Completable {
        return data.upsert(messages).toCompletable()
    }

    private fun <T> Return<ReactiveResult<T>>.observeList(): Observable<List<T>> {
        return get().observableResult().map { it.toList() }
    }
}