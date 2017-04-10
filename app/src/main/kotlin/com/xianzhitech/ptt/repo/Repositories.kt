package com.xianzhitech.ptt.repo

import android.content.Context
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.addAllLimited
import com.xianzhitech.ptt.ext.first
import com.xianzhitech.ptt.ext.sizeAtLeast
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.repo.storage.*
import rx.Completable
import rx.Observable
import rx.Observer
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.Subject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 *
 * 所有数据来源的接口
 *
 * Created by fanchao on 9/01/16.
 */

class UserRepository(private val userStorage: UserStorage,
                     private val userNotification: Subject<*, *>) {

    fun getUsers(ids: Iterable<String>): QueryResult<List<User>> {
        return RepoQueryResult({
            userStorage.getUsers(ids)
        }, userNotification)
    }

    fun saveUsers(users: Iterable<User>): UpdateResult {
        return RepoUpdateResult({
            userStorage.saveUsers(users)
        }, userNotification)
    }

    fun getUser(userId: String?): QueryResult<User?> {
        return RepoQueryResult({
            userId?.let { userStorage.getUsers(listOf(it)).map { it.firstOrNull() } } ?: Single.just<User>(null)
        }, userNotification)
    }

    fun clear(): UpdateResult {
        return RepoUpdateResult({
            userStorage.clear()
        }, userNotification)
    }
}

class GroupRepository(private val groupStorage: GroupStorage,
                      private val groupNotification: Subject<*, *>) {

    fun getGroups(groupIds: Iterable<String>): QueryResult<List<Group>> {
        return RepoQueryResult({
            groupStorage.getGroups(groupIds)
        }, groupNotification)
    }

    fun saveGroups(groups: Iterable<Group>): UpdateResult {
        return RepoUpdateResult({
            groupStorage.saveGroups(groups)
        }, groupNotification)
    }

    fun clear(): UpdateResult {
        return RepoUpdateResult({
            groupStorage.clear()
        }, groupNotification)
    }
}

class RoomRepository(private val roomStorage: RoomStorage,
                     private val groupStorage: GroupStorage,
                     private val userStorage: UserStorage,
                     private val roomNotification: Subject<*, *>,
                     private val userNotification: Subject<*, *>,
                     private val groupNotification: Subject<*, *>) {

    fun getAllRooms(): QueryResult<List<RoomModel>> {
        return RepoQueryResult({
            roomStorage.getAllRooms()
        }, roomNotification)
    }

    fun getRooms(roomIds: Iterable<String>): QueryResult<List<RoomModel>> {
        return RepoQueryResult({
            roomStorage.getRooms(roomIds)
        }, roomNotification)
    }

    fun updateRoomName(roomId : String, name : String) : UpdateResult {
        return RepoUpdateResult({
            roomStorage.updateRoomName(roomId, name)
        }, roomNotification)
    }

    fun getRoom(roomId: String?): QueryResult<RoomModel?> {
        if (roomId == null) {
            return nullResult()
        }

        return RepoQueryResult({
            roomStorage.getRooms(listOf(roomId)).map { it.firstOrNull() }
        }, roomNotification)
    }

    fun getRoomMembers(roomId: String?, maxMemberCount: Int = Constants.MAX_MEMBER_NAME_DISPLAY_COUNT, excludeUserIds: List<String?> = emptyList()): QueryResult<List<User>> {
        if (roomId == null) {
            return fixedResult(emptyList())
        }

        return RepoQueryResult({
            roomStorage.getRooms(listOf(roomId))
                .flatMap {
                    val room = it.firstOrNull() ?: return@flatMap Single.just(emptyList<String>())
                    val userIds = LinkedHashSet<String>()
                    val filter = { it : String? -> excludeUserIds.contains(it).not() }

                    var needsGroupMembers = false

                    if (userIds.addAllLimited(maxMemberCount, listOf(room.ownerId), filter)) {
                        if (userIds.addAllLimited(maxMemberCount, room.extraMemberIds, filter)) {
                            needsGroupMembers = true
                        }
                    }

                    if (needsGroupMembers) {
                        groupStorage.getGroups(room.associatedGroupIds)
                            .map { groups : List<Group> ->
                                groups.forEach { userIds.addAllLimited(maxMemberCount, it.memberIds, filter) }
                                userIds
                            }
                    }
                    else {
                        Single.just(userIds)
                    }
                }
                .flatMap { userStorage.getUsers(it, ArrayList(it.size)) }
        }, Observable.merge(userNotification, groupNotification, roomNotification))
    }

    fun getRoomName(roomId: String?, maxDisplayMemberNames: Int = Constants.MAX_MEMBER_NAME_DISPLAY_COUNT, excludeUserIds: List<String?> = emptyList(),
                    separator: CharSequence = "、", ellipsizeEnd: CharSequence = " 等"): QueryResult<RoomName?> {
        if (roomId == null) {
            return fixedResult(RoomName.EMPTY)
        }

        return RepoQueryResult({
            roomStorage.getRooms(listOf(roomId))
                .flatMap {
                    val room = it.firstOrNull() ?: return@flatMap Single.just<RoomName>(null)

                    // 有预定房间名称, 直接返回
                    if (room.name.isNullOrEmpty().not()) {
                        return@flatMap Single.just(RoomName(room.name, false))
                    }

                    // 有一个相关组且没有额外的用户, 则返回相关组的名称
                    if (room.associatedGroupIds.sizeAtLeast(1) && room.extraMemberIds.sizeAtLeast(1).not()) {
                        return@flatMap groupStorage.getGroups(room.associatedGroupIds.first(1))
                            .map { it.firstOrNull()?.name?.let { RoomName(it, false) } }
                    }

                    // 否则返回成员名称组合
                    getRoomMembers(roomId, maxDisplayMemberNames + 1, excludeUserIds).getAsync()
                        .map { members ->
                            val usedMemberList: List<User>
                            val ellipizes: Boolean
                            if (members.size > maxDisplayMemberNames) {
                                usedMemberList = members.subList(0, maxDisplayMemberNames - 1)
                                ellipizes = true
                            } else {
                                usedMemberList = members
                                ellipizes = false
                            }

                            val name = usedMemberList.joinToString(separator = separator, transform = { it.name })
                            RoomName(if (ellipizes) name + ellipsizeEnd else name, usedMemberList.size == 1)
                        }
                }
        }, Observable.merge(userNotification, groupNotification, roomNotification))
    }

    fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String): UpdateResult {
        return RepoUpdateResult({
            roomStorage.updateLastRoomSpeaker(roomId, time, speakerId)
        }, roomNotification)
    }

    fun updateLastRoomActiveTime(roomId: String, time: Date? = null): UpdateResult {
        return RepoUpdateResult({
            roomStorage.updateLastActiveTime(roomId, time ?: Date())
        }, roomNotification)
    }

    fun saveRooms(rooms: Iterable<Room>): UpdateResult {
        return RepoUpdateResult({
            roomStorage.saveRooms(rooms)
        }, roomNotification)
    }

    fun removeRooms(roomIds : Iterable<String>) : UpdateResult {
        return RepoUpdateResult({
            roomStorage.removeRooms(roomIds)
        }, roomNotification)
    }

    fun clear(): UpdateResult {
        return RepoUpdateResult({
            roomStorage.clear()
        }, roomNotification)
    }
}

class ContactRepository(private val contactStorage: ContactStorage,
                        private val userNotification: Subject<*, *>,
                        private val groupNotification: Subject<*, *>) {

    fun getContactItems(): QueryResult<List<NamedModel>> {
        return RepoQueryResult({
            contactStorage.getContactItems()
        }, userNotification, groupNotification)
    }

    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>): UpdateResult {
        return RepoUpdateResult({
            contactStorage.replaceAllContacts(users, groups)
        }, userNotification, groupNotification)
    }

    fun getAllContactUsers(): QueryResult<List<User>> {
        return RepoQueryResult({
            contactStorage.getAllContactUsers()
        }, userNotification)
    }

    fun clear(): UpdateResult {
        return RepoUpdateResult({
            contactStorage.clear()
        }, userNotification, groupNotification)
    }
}

class MessageRepository(private val messageStorage: MessageStorage,
                        private val messageNotification: Subject<*, *>) {

    fun getAllMessages(roomId: String,
                       latestId : String?,
                       count : Int) : QueryResult<List<Message>> {
        return RepoQueryResult({
            messageStorage.getMessages(roomId, latestId, count)
        }, messageNotification)
    }

    fun saveMessage(messages : List<Message>) : UpdateResult {
        return RepoUpdateResult({
            Completable.fromSingle(messageStorage.saveMessages(messages))
        }, messageNotification)
    }

}

interface QueryResult<T> {
    fun getAsync(): Single<T>
    fun observe(): Observable<T>
}

interface UpdateResult {
    fun execAsync(notifyChanges : Boolean = true): Completable
}

data class RoomName(val name: String,
                    val isSingleMember: Boolean) {
    companion object {
        val EMPTY = RoomName("", false)
    }
}

fun RoomName?.getInRoomDescription(context: Context): CharSequence {
    return when {
        this == null || name.isNullOrBlank() -> R.string.in_room_fallback.toFormattedString(context)
        isSingleMember -> R.string.in_room_with_individual.toFormattedString(context, name)
        else -> R.string.in_room_with_groups.toFormattedString(context, name)
    }
}

fun RoomName?.getInvitationDescription(context: Context, inviterId: String, inviter: User?): CharSequence {
    val inviterName = inviter?.name ?: inviterId
    return when {
        this == null || name.isNullOrBlank() || isSingleMember -> R.string.invite_you_to_join_by_whom.toFormattedString(context, inviterName)
        else -> R.string.invite_you_to_join_group_by_whom.toFormattedString(context, inviterName, name)
    }
}

private class RepoUpdateResult(private val func: () -> Completable,
                               vararg private val notifications: Observer<*>) : UpdateResult {

    override fun execAsync(notifyChanges: Boolean): Completable {
        return if (notifyChanges) {
            func().doOnCompleted {
                notifications.forEach { it.onNext(null) }
            }
        } else {
            func()
        }
    }
}

private object NullQueryResult : QueryResult<Any?> {
    override fun getAsync(): Single<Any?> {
        return Single.just(null)
    }

    override fun observe(): Observable<Any?> {
        return Observable.just(null)
    }
}

private fun <T> nullResult(): QueryResult<T> {
    return NullQueryResult as QueryResult<T>
}

private fun <T> fixedResult(obj: T): QueryResult<T> {
    return object : QueryResult<T> {
        override fun getAsync(): Single<T> {
            return Single.just(obj)
        }

        override fun observe(): Observable<T> {
            return Observable.just(obj)
        }

    }
}

private class RepoQueryResult<T>(private val func : () -> Single<T>,
                                 vararg private val eventNotifications: Observable<*>) : QueryResult<T> {

    override fun getAsync(): Single<T> {
        return func()
    }

    override fun observe(): Observable<T> {
        return Observable.merge(eventNotifications)
                .debounce(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .startWith(Unit)
                .switchMap { func().toObservable() }
    }
}

