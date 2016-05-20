package com.xianzhitech.ptt.repo.storage

import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import java.util.*


/**
 * 用户相关的接口
 */
interface UserStorage {
    fun getUsers(ids: Iterable<String>, out: MutableList<User> = arrayListOf()): List<User>
    fun saveUsers(users: Iterable<User>)
}

/**
 * 群相关的接口
 */
interface GroupStorage {
    fun getGroups(groupIds: Iterable<String>, out: MutableList<Group> = arrayListOf()): List<Group>
    fun saveGroups(groups: Iterable<Group>)
}

/**
 * 会话数据的接口
 */
interface RoomStorage {
    fun getAllRooms(): List<Room>
    fun getRooms(roomIds: Iterable<String>): List<Room>
    fun updateLastRoomActiveUser(roomId: String, activeTime: Date, activeMemberId: String)
    fun saveRooms(rooms: Iterable<Room>)
    fun clearRooms()
}

/**
 * 联系人接口
 */
interface ContactStorage {
    fun getContactItems(): List<Model>
    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>)
}