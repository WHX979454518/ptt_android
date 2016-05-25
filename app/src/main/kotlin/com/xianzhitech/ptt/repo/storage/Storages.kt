package com.xianzhitech.ptt.repo.storage

import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import java.util.*


/**
 * 用户相关的接口
 */
interface UserStorage {
    fun getUsers(ids: Iterable<String>, out: MutableList<User> = arrayListOf()): List<User>
    fun saveUsers(users: Iterable<User>)
    fun clear()
}

/**
 * 群相关的接口
 */
interface GroupStorage {
    fun getGroups(groupIds: Iterable<String>, out: MutableList<Group> = arrayListOf()): List<Group>
    fun saveGroups(groups: Iterable<Group>)
    fun clear()
}

/**
 * 会话数据的接口
 */
interface RoomStorage {
    fun getAllRooms(): List<RoomModel>
    fun getRooms(roomIds: Iterable<String>): List<RoomModel>
    fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String)
    fun updateLastActiveTime(roomId: String, time: Date)
    fun saveRooms(rooms: Iterable<Room>)
    fun clear()
}

/**
 * 联系人接口
 */
interface ContactStorage {
    fun getContactItems(): List<Model>
    fun getAllContactUsers() : List<User>
    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>)
    fun clear()
}