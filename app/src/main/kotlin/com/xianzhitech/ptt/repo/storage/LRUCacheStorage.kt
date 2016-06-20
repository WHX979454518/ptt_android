package com.xianzhitech.ptt.repo.storage

import android.support.v4.util.LruCache
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


class UserLRUCacheStorage(private val userStorage: UserStorage) : UserStorage, BaseLRUCacheStorage<User>() {
    override fun getUsers(ids: Iterable<String>, out: MutableList<User>): List<User> {
        return getOrFetch(ids, { userStorage.getUsers(it) }, out)
    }

    override fun saveUsers(users: Iterable<User>) {
        invalidateModels(users)
        userStorage.saveUsers(users)
    }

    override fun clear() {
        clearCache()
    }
}

class GroupLRUCacheStorage(private val groupStorage: GroupStorage) : GroupStorage by groupStorage, BaseLRUCacheStorage<Group>() {
    override fun saveGroups(groups: Iterable<Group>) {
        invalidateModels(groups)
        groupStorage.saveGroups(groups)
    }

    override fun getGroups(groupIds: Iterable<String>, out: MutableList<Group>): List<Group> {
        return getOrFetch(groupIds, { groupStorage.getGroups(it) }, out)
    }

    override fun clear() {
        clearCache()
    }
}

class RoomLRUCacheStorage(private val roomStorage: RoomStorage) : RoomStorage by roomStorage, BaseLRUCacheStorage<RoomModel>() {
    private var allRoomIds: HashSet<String>? = null
    private var allRoomIdsLock = Any()

    override fun getAllRooms(): List<RoomModel> {
        return synchronized(allRoomIdsLock, {
            val ret: List<RoomModel>
            if (allRoomIds == null) {
                ret = roomStorage.getAllRooms()
                cacheLock.write {
                    ret.forEach { cache.put(it.id, it) }
                }
                allRoomIds = ret.transform { it.id }.toHashSet()
            } else {
                ret = getOrFetch(allRoomIds!!, { roomStorage.getRooms(it) }, arrayListOf())
            }

            ret
        })
    }

    override fun getRooms(roomIds: Iterable<String>): List<RoomModel> {
        return getOrFetch(roomIds, { roomStorage.getRooms(it) }, arrayListOf())
    }

    override fun updateRoomName(roomId: String, name: String) {
        invalidateIds(listOf(roomId))
        roomStorage.updateRoomName(roomId, name)
    }

    override fun updateLastRoomSpeaker(roomId: String, time: Date, speakerId: String) {
        invalidateIds(listOf(roomId))
        roomStorage.updateLastRoomSpeaker(roomId, time, speakerId)
    }

    override fun updateLastActiveTime(roomId: String, time: Date) {
        invalidateIds(listOf(roomId))
        roomStorage.updateLastActiveTime(roomId, time)
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

    override fun clear() {
        roomStorage.clear()
        cacheLock.write { cache.evictAll() }
        synchronized(allRoomIdsLock, { allRoomIds?.clear() })
    }
}

open class BaseLRUCacheStorage<T : Model>(capacity: Int = 10240) {
    protected val cache = LruCache<String, T>(capacity)
    protected val cacheLock = ReentrantReadWriteLock()

    protected inline fun getOrFetch(ids: Iterable<String>, fetch: (Iterable<String>) -> List<T>, out: MutableList<T>): List<T> {
        val missedIds = arrayListOf<String>()
        cacheLock.read {
            ids.forEach {
                val result = cache[it]
                if (result == null) {
                    missedIds.add(it)
                } else {
                    out.add(result)
                }
            }

            if (missedIds.isNotEmpty()) {
                // Now go fetching.
                cacheLock.write {
                    // Upgrading from read lock IS NOT THREAD SAFE!!!
                    // Check again for missed ids in cache to avoid race-condition
                    val missedIter = missedIds.iterator()
                    while (missedIter.hasNext()) {
                        val result = cache[missedIter.next()]
                        if (result != null) {
                            out.add(result)
                            missedIter.remove()
                        }
                    }

                    // Now fetch
                    if (missedIds.isNotEmpty()) {
                        fetch(missedIds).forEach {
                            cache.put(it.id, it)
                            out.add(it)
                        }
                    }
                }
            }
        }

        return out
    }

    protected fun invalidateModels(models: Iterable<Model>) {
        cacheLock.write {
            models.forEach { cache.remove(it.id) }
        }
    }

    protected fun invalidateIds(ids: Iterable<String>) {
        cacheLock.write {
            ids.forEach { cache.remove(it) }
        }
    }

    protected fun clearCache() {
        cacheLock.write {
            cache.evictAll()
        }
    }
}