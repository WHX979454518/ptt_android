package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import rx.Observable

/**
 *
 * 所有数据来源的接口
 *
 * Created by fanchao on 9/01/16.
 */

/**
 * 用户相关的接口
 */
interface UserRepository {
    fun getUser(id: String): Observable<User?>
    fun getUsers(ids : Iterable<String>) : Observable<List<User>>
    fun getAllUsers(): Observable<List<User>>
    fun replaceAllUsers(users: Iterable<User>): Observable<Unit>
    fun saveUser(user : User) : Observable<User>
}

/**
 * 群相关的接口
 */
interface GroupRepository {
    fun getGroup(groupId: String): Observable<Group?>
    fun getGroupMembers(groupId: String): Observable<List<User>>
    fun updateGroupMembers(groupId: String, memberIds: Iterable<String>): Observable<Unit>
    fun replaceAllGroups(groups: Iterable<Group>, groupMembers: Map<String, Iterable<String>>): Observable<Unit>
}

/**
 * 会话数据的接口
 */
interface RoomRepository {
    fun clearRooms() : Observable<Unit>
    fun getRoom(roomId: String): Observable<Room?>
    fun getRoomMembers(roomId: String): Observable<List<User>>
    fun updateRoom(room: Room, memberIds: Iterable<String>): Observable<Room>
    fun updateRoomMembers(roomId: String, memberIds: Iterable<String>): Observable<Unit>
    fun getRoomsWithMemberNames(maxMember: Int): Observable<List<RoomWithMemberNames>>
    fun getRoomWithMemberNames(roomId: String, maxMember: Int): Observable<RoomWithMemberNames?>
    fun getRoomWithMembers(roomId: String) : Observable<RoomWithMembers?>
}

/**
 * 联系人接口
 */
interface ContactRepository {
    fun getContactItems(): Observable<List<ContactItem>>
    fun searchContactItems(searchTerm: String): Observable<List<ContactItem>>
    fun replaceAllContacts(userIds: Iterable<String>, groupIds: Iterable<String>): Observable<Unit>
}

/**
 * 一个包括了房间信息和局部成员名的数据结构
 */
data class RoomWithMemberNames(val room: Room,
                               val memberNames: List<String>,
                               val memberCount: Int)

/**
 * 一个包括了房间信息和成员信息的数据结构
 */
data class RoomWithMembers(val room: Room,
                           val members : List<User>)

/**
 * 提供一个参数可为空的查询房间成员的方法
 */
fun RoomRepository.optRoomWithMembers(roomId: String?): Observable<RoomWithMembers?> =
        roomId?.let { getRoomWithMembers(it) } ?: Observable.just<RoomWithMembers?>(null)

/**
 * 提供一个参数可为空的查询房间成员名的方法
 */
fun RoomRepository.optRoomWithMemberNames(roomId: String?, maxMember: Int = Constants.MAX_MEMBER_DISPLAY_COUNT): Observable<RoomWithMemberNames?> =
        roomId?.let { getRoomWithMemberNames(it, maxMember) } ?: Observable.just<RoomWithMemberNames?>(null)

/**
 * 提供一个参数可为空的查询用户的方法
 */
fun UserRepository.optUser(id: String?): Observable<User?> =
        id?.let { getUser(id) } ?: Observable.just<User?>(null)