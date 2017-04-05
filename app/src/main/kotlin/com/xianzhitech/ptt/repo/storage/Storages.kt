package com.xianzhitech.ptt.repo.storage

import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.repo.RoomModel
import rx.Completable
import rx.Single
import java.util.*


/**
 * 用户相关的接口
 */
interface UserStorage {
    fun getUsers(ids: Iterable<String>, out: MutableList<User> = arrayListOf()): Single<List<User>>
    fun saveUsers(users: Iterable<User>) : Completable
    fun clear() : Completable
}

/**
 * 群相关的接口
 */
interface GroupStorage {
    fun getGroups(groupIds: Iterable<String>, out: MutableList<Group> = arrayListOf()): Single<List<Group>>
    fun saveGroups(groups: Iterable<Group>) : Completable
    fun clear() : Completable
}

/**
 * 会话数据的接口
 */
interface RoomStorage {
    fun getAllRooms(): Single<List<RoomModel>>
    fun updateRoomName(roomId: String, name: String) : Completable
    fun getRooms(roomIds: Iterable<String>, out : MutableList<RoomModel> = arrayListOf()): Single<List<RoomModel>>
    fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String) : Completable
    fun updateLastActiveTime(roomId: String, time: Date) : Completable
    fun saveRooms(rooms: Iterable<Room>) : Completable
    fun removeRooms(roomIds: Iterable<String>) : Completable
    fun clear() : Completable
}

interface MessageStorage {
    fun saveMessages(messages: Iterable<Message>) : Single<List<Message>>
    fun getMessages(roomId: String, latestId: String?, count: Int) : Single<List<Message>>
    fun clear() : Completable
}

/**
 * 联系人接口
 */
interface ContactStorage {
    fun getContactItems(): Single<List<NamedModel>>
    fun getAllContactUsers(): Single<List<User>>
    fun replaceAllContacts(users: Iterable<User>, groups: Iterable<Group>) : Completable
    fun clear() : Completable
}