package com.xianzhitech.ptt.repo

import android.content.Context
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.first
import com.xianzhitech.ptt.ext.sizeAtLeast
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.storage.ContactStorage
import com.xianzhitech.ptt.repo.storage.GroupStorage
import com.xianzhitech.ptt.repo.storage.RoomStorage
import com.xianzhitech.ptt.repo.storage.UserStorage
import rx.*
import rx.Observable
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 *
 * 所有数据来源的接口
 *
 * Created by fanchao on 9/01/16.
 */

class UserRepository(private val userStorage: UserStorage,
                     private val userNotification: PublishSubject<Unit>) {

    fun getUsers(ids: Iterable<String>): QueryResult<List<User>> {
        return RepoQueryResult({
            userStorage.getUsers(ids)
        }, userNotification)
    }

    fun saveUsers(users: Iterable<User>) : UpdateResult {
        return RepoUpdateResult({
            userStorage.saveUsers(users)
        }, userNotification)
    }

    fun getUser(userId: String?): QueryResult<User?> {
        return RepoQueryResult({
            userId?.let { userStorage.getUsers(listOf(it)).firstOrNull() }
        }, userNotification)
    }

    fun clear() : UpdateResult {
        return RepoUpdateResult({
            userStorage.clear()
        }, userNotification)
    }
}

class GroupRepository(private val groupStorage: GroupStorage,
                      private val groupNotification: PublishSubject<Unit>) {

    fun getGroups(groupIds: Iterable<String>): QueryResult<List<Group>> {
        return RepoQueryResult({
            groupStorage.getGroups(groupIds)
        }, groupNotification)
    }

    fun saveGroups(groups: Iterable<Group>) : UpdateResult {
        return RepoUpdateResult({
            groupStorage.saveGroups(groups)
        }, groupNotification)
    }

    fun clear() : UpdateResult {
        return RepoUpdateResult({
            groupStorage.clear()
        }, groupNotification)
    }
}

class RoomRepository(private val roomStorage: RoomStorage,
                     private val groupStorage: GroupStorage,
                     private val userStorage: UserStorage,
                     private val roomNotification: PublishSubject<Unit>,
                     private val userNotification: PublishSubject<Unit>,
                     private val groupNotification: PublishSubject<Unit>) {

    fun getAllRooms() : QueryResult<List<RoomModel>> {
        return RepoQueryResult({
            roomStorage.getAllRooms()
        }, roomNotification)
    }

    fun getRooms(roomIds: Iterable<String>): QueryResult<List<Room>> {
        return RepoQueryResult({
            roomStorage.getRooms(roomIds)
        }, roomNotification)
    }

    fun getRoom(roomId : String?) : QueryResult<Room?> {
        if (roomId == null) {
            return nullResult()
        }

        return RepoQueryResult({
            roomStorage.getRooms(listOf(roomId)).firstOrNull()
        }, roomNotification)
    }

    fun getRoomMembers(roomId: String?, maxMemberCount : Int = Constants.MAX_MEMBER_NAME_DISPLAY_COUNT, excludeUserIds: Array<String?> = emptyArray()) : QueryResult<List<User>> {
        if (roomId == null) {
            return fixedResult(emptyList())
        }

        return RepoQueryResult({
            val room = roomStorage.getRooms(listOf(roomId)).first()
            val groups = groupStorage.getGroups(room.associatedGroupIds)
            val memberIds = linkedSetOf<String>()

            if (memberIds.size < maxMemberCount && excludeUserIds.contains(room.ownerId).not()) {
                memberIds.add(room.ownerId)
            }

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
        }, Observable.merge(userNotification, groupNotification, roomNotification))
    }

    fun getRoomName(roomId: String?, maxDisplayMemberNames : Int = Constants.MAX_MEMBER_NAME_DISPLAY_COUNT, excludeUserIds : Array<String?> = emptyArray(),
                    separator : CharSequence = "、", ellipsizeEnd : CharSequence = " 等") : QueryResult<RoomName?> {
        if (roomId == null) {
            return fixedResult(RoomName.EMPTY)
        }

        return RepoQueryResult({
            val room = roomStorage.getRooms(listOf(roomId)).firstOrNull() ?: return@RepoQueryResult null

            // 有预定房间名称, 直接返回
            if (room.name.isNullOrEmpty().not()) {
                return@RepoQueryResult RoomName(room.name, false)
            }

            // 有一个相关组且没有额外的用户, 则返回相关组的名称
            if (room.associatedGroupIds.sizeAtLeast(1) && room.extraMemberIds.sizeAtLeast(1).not()) {
                val groupName = groupStorage.getGroups(room.associatedGroupIds.first(1)).firstOrNull()?.name
                if (groupName != null) {
                    return@RepoQueryResult RoomName(groupName, false)
                }
            }

            // 否则返回成员名称组合
            val members = getRoomMembers(roomId, maxDisplayMemberNames + 1, excludeUserIds).call()
            val usedMemberList : List<User>
            val ellipizes : Boolean
            if (members.size > maxDisplayMemberNames) {
                usedMemberList = members.subList(0, maxDisplayMemberNames - 1)
                ellipizes = true
            }
            else {
                usedMemberList = members
                ellipizes = false
            }

            val name = usedMemberList.joinToString(separator = separator, transform = { it.name })
            RoomName(if (ellipizes) name + ellipsizeEnd else name, usedMemberList.size == 1)
        }, Observable.merge(userNotification, groupNotification, roomNotification))
    }

    fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String) : UpdateResult {
        return RepoUpdateResult({
            roomStorage.updateLastRoomSpeaker(roomId, time, speakerId)
        }, roomNotification)
    }

    fun updateLastRoomActiveTime(roomId : String, time: Date? = null) : UpdateResult {
        return RepoUpdateResult({
            roomStorage.updateLastActiveTime(roomId, time ?: Date())
        }, roomNotification)
    }

    fun saveRooms(rooms: Iterable<Room>) : UpdateResult {
        return RepoUpdateResult({
            roomStorage.saveRooms(rooms)
        }, roomNotification)
    }

    fun clear() : UpdateResult {
        return RepoUpdateResult({
            roomStorage.clear()
        }, roomNotification)
    }
}

class ContactRepository(private val contactStorage: ContactStorage,
                        private val userNotification: PublishSubject<Unit>,
                        private val groupNotification: PublishSubject<Unit>) {

    fun getContactItems(): QueryResult<List<Model>> {
        return RepoQueryResult({
            contactStorage.getContactItems()
        }, userNotification, groupNotification)
    }

    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>) : UpdateResult {
        return RepoUpdateResult({
            contactStorage.replaceAllContacts(users, groups)
        }, userNotification, groupNotification)
    }

    fun getAllContactUsers() : QueryResult<List<User>> {
        return RepoQueryResult({
            contactStorage.getAllContactUsers()
        }, userNotification)
    }

    fun clear() : UpdateResult {
        return RepoUpdateResult({
            contactStorage.clear()
        }, userNotification, groupNotification)
    }
}

interface QueryResult<T> : Callable<T> {
    fun getAsync(scheduler: Scheduler = Schedulers.computation()): Single<T>
    fun observe(scheduler: Scheduler = Schedulers.computation()): Observable<T>
}

interface UpdateResult {
    fun exec()
    fun execAsync(scheduler: Scheduler = Schedulers.computation()): Completable
}

data class RoomName(val name : String,
                    val isSingleMember : Boolean) {
    companion object {
        val EMPTY = RoomName("", false)
    }
}

fun RoomName?.getInRoomDescription(context: Context) : CharSequence {
    return if (this == null || name.isNullOrBlank()) {
        R.string.in_room_fallback.toFormattedString(context)
    }
    else if (isSingleMember) {
        R.string.in_room_with_individual.toFormattedString(context, name)
    }
    else {
        R.string.in_room_with_groups.toFormattedString(context, name)
    }
}

private class RepoUpdateResult(private val func: () -> Unit,
                               vararg private val notifications : Observer<Unit>) : UpdateResult {
    override fun exec() {
        func()
        notifications.forEach { it.onNext(Unit) }
    }

    override fun execAsync(scheduler: Scheduler): Completable {
        return Completable.fromCallable { exec() }.subscribeOn(scheduler)
    }
}

private object NullQueryResult : QueryResult<Any?> {
    override fun call(): Any? {
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

private fun <T> fixedResult(obj : T) : QueryResult<T> {
    return object : QueryResult<T> {
        override fun call(): T {
            return obj
        }

        override fun getAsync(scheduler: Scheduler): Single<T> {
            return Single.just(obj)
        }

        override fun observe(scheduler: Scheduler): Observable<T> {
            return Observable.just(obj)
        }

    }
}

private class RepoQueryResult<T>(private val func: () -> T,
                                 vararg private val eventNotifications: Observable<Unit>) : QueryResult<T>, Callable<T> {
    override fun call(): T {
        return func()
    }

    override fun getAsync(scheduler: Scheduler): Single<T> {
        return Single.fromCallable(this).subscribeOn(scheduler)
    }

    override fun observe(scheduler: Scheduler): Observable<T> {
        return Observable.merge(eventNotifications)
                .debounce(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .startWith(Unit)
                .observeOn(scheduler)
                .map { func() }
    }
}

