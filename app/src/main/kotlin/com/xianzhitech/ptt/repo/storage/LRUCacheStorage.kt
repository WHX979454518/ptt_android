package com.xianzhitech.ptt.repo.storage

import android.support.v4.util.LruCache
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
        userStorage.saveUsers(users)
        invalidateModels(users)
    }
}

class GroupLRUCacheStorage(private val groupStorage: GroupStorage) : GroupStorage by groupStorage, BaseLRUCacheStorage<Group>() {
    override fun saveGroups(groups: Iterable<Group>) {
        groupStorage.saveGroups(groups)
        invalidateModels(groups)
    }

    override fun getGroups(groupIds: Iterable<String>): List<Group> {
        return getOrFetch(groupIds, { groupStorage.getGroups(it) })
    }
}

class RoomLRUCacheStorage(private val roomStorage: RoomStorage) : RoomStorage by roomStorage, BaseLRUCacheStorage<Room>() {
    private var initialized = false

    private fun initializeIfNeeded() {
        synchronized(this, {
            if (initialized.not()) {
                val rooms = roomStorage.getAllRooms()
                cacheLock.write {
                    rooms.forEach {
                        cache.put(it.id, it)
                    }
                }
                initialized = true
            }
        })
    }

    override fun getAllRooms(): List<Room> {
        initializeIfNeeded()
        return cacheLock.read {
            cache.snapshot().values.toList()
        }
    }

    override fun getRooms(roomIds: Iterable<String>): List<Room> {
        initializeIfNeeded()
        return getOrFetch(roomIds, { roomStorage.getRooms(it) })
    }

    override fun updateLastRoomActiveUser(roomId: String, activeTime: Date, activeMemberId: String) {
        roomStorage.updateLastRoomActiveUser(roomId, activeTime, activeMemberId)
        invalidateIds(listOf(roomId))
    }

    override fun saveRooms(rooms: Iterable<Room>) {
        roomStorage.saveRooms(rooms)
        invalidateModels(rooms)
    }

    override fun clearRooms() {
        synchronized(this, { initialized = false })
        cacheLock.write { cache.evictAll() }
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