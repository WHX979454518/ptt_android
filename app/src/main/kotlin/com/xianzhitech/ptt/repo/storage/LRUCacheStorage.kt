package com.xianzhitech.ptt.repo.storage

import android.support.v4.util.LruCache
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


class UserLRUCacheStorage(private val userStorage: UserStorage) : UserStorage by userStorage, BaseLRUCacheStorage<User>() {
    override fun getUsers(ids: Iterable<String>): List<User> {
        return getOrFetch(ids, { userStorage.getUsers(it) })
    }

    override fun saveUsers(users: Iterable<User>) {
        invalidateModels(users)
        userStorage.saveUsers(users)
    }
}

class GroupLRUCacheStorage(private val groupStorage: GroupStorage) : GroupStorage by groupStorage, BaseLRUCacheStorage<Group>() {
    override fun saveGroups(groups: Iterable<Group>) {
        invalidateModels(groups)
        groupStorage.saveGroups(groups)
    }

    override fun getGroups(groupIds: Iterable<String>): List<Group> {
        return getOrFetch(groupIds, { groupStorage.getGroups(it) })
    }
}

class RoomLRUCacheStorage(private val roomStorage: RoomStorage) : RoomStorage by roomStorage, BaseLRUCacheStorage<Room>() {
    private var allRoomIds : HashSet<String>? = null
    private var allRoomIdsLock = Any()

    override fun getAllRooms(): List<Room> {
        return synchronized(allRoomIdsLock, {
            val ret : List<Room>
            if (allRoomIds == null) {
                ret = roomStorage.getAllRooms()
                cacheLock.write {
                    ret.forEach { cache.put(it.id, it) }
                }
                allRoomIds = ret.transform { it.id }.toHashSet()
            }
            else {
                ret = getOrFetch(allRoomIds!!, { roomStorage.getRooms(it) })
            }

            ret
        })
    }

    override fun getRooms(roomIds: Iterable<String>): List<Room> {
        return getOrFetch(roomIds, { roomStorage.getRooms(it) })
    }

    override fun updateLastRoomActiveUser(roomId: String, activeTime: Date, activeMemberId: String) {
        invalidateIds(listOf(roomId))
        roomStorage.updateLastRoomActiveUser(roomId, activeTime, activeMemberId)
    }

    override fun saveRooms(rooms: Iterable<Room>) {
        invalidateModels(rooms)
        roomStorage.saveRooms(rooms)

        // 将所有房间的ID存入缓存中
        return synchronized(allRoomIdsLock, {
            if (allRoomIds == null) {
                allRoomIds = roomStorage.getAllRooms().map { it.id }.toHashSet()
            }

            allRoomIds!!.addAll(rooms.transform { it.id })
        })
    }

    override fun clearRooms() {
        cacheLock.write { cache.evictAll() }
        synchronized(allRoomIdsLock, { allRoomIds?.clear() })
    }
}

open class BaseLRUCacheStorage<T : Model>(capacity : Int = 10240) {
    protected val cache = LruCache<String, T>(capacity)
    protected val cacheLock = ReentrantReadWriteLock()

    protected inline fun getOrFetch(ids : Iterable<String>, fetch : (Iterable<String>) -> List<T>) : List<T> {
        val missedIds = arrayListOf<String>()
        val resultList = arrayListOf<T>()
        cacheLock.read {
            ids.forEach {
                val result = cache[it]
                if (result == null) {
                    missedIds.add(it)
                }
                else {
                    resultList.add(result)
                }
            }
        }

        if (missedIds.isNotEmpty()) {
            val fetchedResult = fetch(missedIds)
            if (fetchedResult.isNotEmpty()) {
                cacheLock.write {
                    fetchedResult.forEach {
                        cache.put(it.id, it)
                    }
                }
            }

            resultList.addAll(fetchedResult)
        }

        return resultList
    }

    protected fun invalidateModels(models : Iterable<Model>) {
        cacheLock.write {
            models.forEach { cache.remove(it.id) }
        }
    }

    protected fun invalidateIds(ids : Iterable<String>) {
        cacheLock.write {
            ids.forEach { cache.remove(it) }
        }
    }
}