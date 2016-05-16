package com.xianzhitech.ptt.repo

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.storage.ContactStorage
import com.xianzhitech.ptt.repo.storage.GroupStorage
import com.xianzhitech.ptt.repo.storage.RoomStorage
import com.xianzhitech.ptt.repo.storage.UserStorage
import rx.Completable
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.util.*

/**
 *
 * 所有数据来源的接口
 *
 * Created by fanchao on 9/01/16.
 */

class UserRepository(private val context: Context,
                     private val userStorage: UserStorage) {
    fun getUsers(ids: Iterable<String>): QueryResult<List<User>> {
        return RepoQueryResult(context, arrayOf(USER_URI), {
            userStorage.getUsers(ids)
        })
    }

    fun saveUsers(users: Iterable<User>) : UpdateResult {
        return RepoUpdateResult(context, arrayOf(USER_URI), {
            userStorage.saveUsers(users)
        })
    }

    fun getUser(userId: String?): QueryResult<User?> {
        return RepoQueryResult(context, arrayOf(USER_URI), {
            if (userId == null) {
                null
            }
            else {
                userStorage.getUsers(listOf(userId)).firstOrNull()
            }
        })
    }
}

class GroupRepository(private val context: Context,
                      private val groupStorage: GroupStorage) {

    fun getGroups(groupIds: Iterable<String>): QueryResult<List<Group>> {
        return RepoQueryResult(context, arrayOf(GROUP_URI), {
            groupStorage.getGroups(groupIds)
        })
    }

    fun saveGroups(groups: Iterable<Group>) : UpdateResult {
        return RepoUpdateResult(context, arrayOf(GROUP_URI), {
            groupStorage.saveGroups(groups)
        })
    }
}

class RoomRepository(private val context: Context,
                     private val roomStorage: RoomStorage,
                     private val groupStorage: GroupStorage,
                     private val userStorage: UserStorage) {

    fun getAllRooms() : QueryResult<List<Room>> {
        return RepoQueryResult(context, arrayOf(ROOM_URI), {
            roomStorage.getAllRooms()
        })
    }

    fun getRooms(roomIds: Iterable<String>): QueryResult<List<Room>> {
        return RepoQueryResult(context, arrayOf(ROOM_URI), {
            roomStorage.getRooms(roomIds)
        })
    }

    fun getRoom(roomId : String?) : QueryResult<Room?> {
        if (roomId == null) {
            return nullResult()
        }

        return RepoQueryResult(context, arrayOf(ROOM_URI), {
            roomStorage.getRooms(listOf(roomId)).firstOrNull()
        })
    }

    fun getRoomMembers(roomId: String?, maxMemberCount : Int = Constants.MAX_MEMBER_DISPLAY_COUNT, excludeUserIds: Array<String> = emptyArray()) : QueryResult<List<User>> {
        if (roomId == null) {
            return nullResult()
        }

        return RepoQueryResult(context, arrayOf(ROOM_URI, GROUP_URI, USER_URI), {
            val room = roomStorage.getRooms(listOf(roomId)).first()
            val groups = groupStorage.getGroups(room.associatedGroupIds)
            val memberIds = linkedSetOf<String>()

            // Add members from associated groups first
            loopGroup@ for (group in groups) {
                for (memberId in group.memberIds) {
                    if (excludeUserIds.contains(memberId)) {
                        continue
                    }

                    memberIds.add(memberId)
                    if (memberIds.size >= maxMemberCount) {
                        break@loopGroup
                    }
                }
            }

            // Add members from 'extraMembers'
            if (memberIds.size < maxMemberCount) {
                for (memberId in room.extraMemberIds) {
                    if (excludeUserIds.contains(memberId)) {
                        continue
                    }

                    memberIds.add(memberId)
                    if (memberIds.size >= maxMemberCount) {
                        break
                    }
                }
            }

            userStorage.getUsers(memberIds)
        })
    }

    fun getRoomName(roomId: String?, maxDisplayMemberNames : Int = 3, excludeUserIds : Array<String> = emptyArray(),
                    separator : CharSequence = "、", ellipsizeEnd : CharSequence = "等") : QueryResult<String?> {
        if (roomId == null) {
            return nullResult()
        }

        return RepoQueryResult(context, arrayOf(ROOM_URI, GROUP_URI, USER_URI), {
            val room = roomStorage.getRooms(listOf(roomId)).first()
            if (room.name.isNullOrEmpty().not()) {
                room.name
            }
            else {
                (getRoomMembers(roomId, maxDisplayMemberNames + 1, excludeUserIds) as RepoQueryResult).map { members ->
                    if (members.size > maxDisplayMemberNames) {
                        members.subList(0, maxDisplayMemberNames - 1).joinToString(separator = separator, transform = {it.name}) + ellipsizeEnd
                    }
                    else {
                        members.joinToString(separator = separator, transform = {it.name})
                    }
                }.get()
            }
        })
    }

    fun updateLastRoomActiveUser(roomId: String, activeTime: Date, activeMemberId: String) : UpdateResult {
        return RepoUpdateResult(context, arrayOf(ROOM_URI), {
            roomStorage.updateLastRoomActiveUser(roomId, activeTime, activeMemberId)
        })
    }

    fun saveRooms(rooms: Iterable<Room>) : UpdateResult {
        return RepoUpdateResult(context, arrayOf(ROOM_URI), {
            roomStorage.saveRooms(rooms)
        })
    }

    fun clearRooms() : UpdateResult {
        return RepoUpdateResult(context, arrayOf(ROOM_URI), {
            roomStorage.clearRooms()
        })
    }
}

class ContactRepository(private val context: Context,
                        private val contactStorage: ContactStorage) {

    fun getContactItems(): QueryResult<List<Model>> {
        return RepoQueryResult(context, arrayOf(USER_URI, GROUP_URI), {
            contactStorage.getContactItems()
        })
    }

    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>) : UpdateResult {
        return RepoUpdateResult(context, arrayOf(GROUP_URI, USER_URI), {
            contactStorage.replaceAllContacts(users, groups)
        })
    }
}

private val BASE_MODEL_URI = Uri.parse("content://com.xianzhi.ptt/")
private val USER_URI = BASE_MODEL_URI.buildUpon().appendPath("users").build()
private val GROUP_URI = BASE_MODEL_URI.buildUpon().appendPath("groups").build()
private val ROOM_URI = BASE_MODEL_URI.buildUpon().appendPath("rooms").build()

interface QueryResult<T> {
    fun get() : T
    fun getAsync(scheduler: Scheduler = Schedulers.computation()): Single<T>
    fun observe(scheduler: Scheduler = Schedulers.computation()): Observable<T>
}

interface UpdateResult {
    fun exec()
    fun execAsync(scheduler: Scheduler = Schedulers.computation()): Completable
}

interface ExtraRoomInfo {
    val lastActiveTime : Date?
    val lastActiveMemberId : String?
}

private val MAIN_HANDLER: Handler by lazy { Handler(Looper.getMainLooper()) }

private class RepoUpdateResult(private val context: Context,
                               private val uris : Array<Uri>,
                               private val func: () -> Unit) : UpdateResult {
    override fun exec() {
        func()
    }

    override fun execAsync(scheduler: Scheduler): Completable {
        return Completable.defer {
            func()
            uris.forEach { context.contentResolver.notifyChange(it, null) }
            Completable.complete()
        }.subscribeOn(scheduler)
    }
}

private object NullQueryResult : QueryResult<Any?> {
    override fun get(): Any? {
        return null
    }

    override fun getAsync(scheduler: Scheduler): Single<Any?> {
        return Single.just(null)
    }

    override fun observe(scheduler: Scheduler): Observable<Any?> {
        return Observable.just(null)
    }
}

private fun <T> nullResult() : QueryResult<T> {
    return NullQueryResult as QueryResult<T>
}

private class RepoQueryResult<T>(private val context: Context,
                                 private val uris : Array<Uri>,
                                 private val func: () -> T) : QueryResult<T> {
    override fun get(): T {
        return func()
    }

    override fun getAsync(scheduler: Scheduler): Single<T> {
        return Single.defer<T> { Single.just(get()) }.subscribeOn(scheduler)
    }

    override fun observe(scheduler: Scheduler): Observable<T> {
        return Observable.create<T> { subscriber ->
            try {
                subscriber.onNext(func())
            } catch(e: Exception) {
                subscriber.onError(e)
                return@create
            }

            if (subscriber.isUnsubscribed.not()) {
                val observer = object : ContentObserver(MAIN_HANDLER) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        scheduler.createWorker().schedule {
                            try {
                                subscriber.onNext(func())
                            } catch(e: Exception) {
                                subscriber.onError(e)
                            }
                        }
                    }
                }

                uris.forEach { context.contentResolver.registerContentObserver(it, true, observer) }
                subscriber.add(Subscriptions.create { context.contentResolver.unregisterContentObserver(observer) })
            }
            else {
                subscriber.onCompleted()
            }
        }.subscribeOn(scheduler)
    }

    fun <R> map(transform : (T) -> R) : RepoQueryResult<R> {
        return RepoQueryResult(context, uris, { transform(func()) })
    }
}

