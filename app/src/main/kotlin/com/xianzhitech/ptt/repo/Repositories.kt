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
    fun getGroupsWithMembers(groupIds: Iterable<String>, maxMember: Int) : Observable<List<GroupWithMembers>>
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
    fun updateRoomLastActiveUser(roomId: String, activeUserId: String) : Observable<Unit>
    fun getRoomsWithMembers(maxMember: Int): Observable<List<RoomWithMembers>>
    fun getRoomWithMembers(roomId: String, maxMember: Int = Constants.MAX_MEMBER_DISPLAY_COUNT) : Observable<RoomWithMembers?>
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
 * 一个包括了房间信息和成员信息的数据结构
 */
data class RoomWithMembers(val room: Room,
                           val members : List<User>,
                           val memberCount : Int) : Room by room

/**
 * 一个包括了群组信息和成员信息的数据结构
 */
data class GroupWithMembers(val group : Group,
                            val members : List<User>,
                            val memberCount : Int) : Group by group


/**
 * 提供一个参数可为空的查询房间成员名的方法
 */
fun RoomRepository.optRoomWithMembers(roomId: String?, maxMember: Int = Constants.MAX_MEMBER_DISPLAY_COUNT): Observable<RoomWithMembers?> =
        roomId?.let { getRoomWithMembers(it, maxMember) } ?: Observable.just<RoomWithMembers?>(null)

/**
 * 提供一个参数可为空的查询用户的方法
 */
fun UserRepository.optUser(id: String?): Observable<User?> =
        id?.let { getUser(id) } ?: Observable.just<User?>(null)