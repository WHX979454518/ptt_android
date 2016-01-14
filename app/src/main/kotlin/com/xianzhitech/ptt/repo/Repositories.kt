package com.xianzhitech.ptt.repo

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

interface UserRepository {
    fun getUser(id: String): Observable<User?>
    fun getUsers(ids : Iterable<String>) : Observable<List<User>>
    fun getAllUsers(): Observable<List<User>>
    fun replaceAllUsers(users: Iterable<User>): Observable<Unit>
}

interface GroupRepository {
    fun getGroup(groupId: String): Observable<Group?>
    fun getGroupMembers(groupId: String): Observable<List<User>>
    fun updateGroupMembers(groupId: String, memberIds: Iterable<String>): Observable<Unit>
    fun replaceAllGroups(groups: Iterable<Group>, groupMembers: Map<String, Iterable<String>>): Observable<Unit>
}

interface RoomRepository {
    fun clearRooms() : Observable<Unit>
    fun getRoom(roomId: String): Observable<Room?>
    fun getRoomMembers(roomId: String): Observable<List<User>>
    fun updateRoom(room: Room, memberIds: Iterable<String>): Observable<Room>
    fun updateRoomMembers(roomId: String, memberIds: Iterable<String>): Observable<Unit>
    fun getRoomsWithMemberNames(maxMember: Int): Observable<List<RoomWithMemberNames>>
    fun getRoomWithMemberNames(roomId: String, maxMember: Int): Observable<RoomWithMemberNames?>
}

interface ContactRepository {
    fun getContactItems(): Observable<List<ContactItem>>
    fun searchContactItems(searchTerm: String): Observable<List<ContactItem>>
    fun replaceAllContacts(userIds: Iterable<String>, groupIds: Iterable<String>): Observable<Unit>
}

data class RoomWithMemberNames(val room: Room,
                               val memberNames: List<String>,
                               val memberCount: Int)