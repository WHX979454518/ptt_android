package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.db.Database
import com.xianzhitech.ptt.db.DatabaseFactory
import com.xianzhitech.ptt.db.JDBCDatabase
import com.xianzhitech.ptt.db.TableDefinition
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import java.util.*

/**
 * Created by fanchao on 10/01/16.
 */
@RunWith(MockitoJUnitRunner::class)
class LocalRepositoryTest {
    private lateinit var localRepository: LocalRepository

    private val person1 = UserImpl(id = "1", name = "hello", privilegesText = EnumSet.allOf(Privilege::class.java).toDatabaseString(), avatar = null, level = 100)
    private val person2 = UserImpl(id = "2", name = "hello2", privilegesText = EnumSet.allOf(Privilege::class.java).toDatabaseString(), avatar = null, level = 100)
    private val group1 = GroupImpl("1", "hello1", "group1", null)
    private val room1 = RoomImpl("1", "Room1", "", person1.id, "room1", null)
    private val groupMembers = hashMapOf(Pair(group1.id, listOf(person1.id, person2.id)))
    private val roomMembers = hashMapOf(Pair(room1.id, listOf(person1.id, person2.id)))

    @Before
    fun init() {
        localRepository = LocalRepository(object : DatabaseFactory {
            override fun createDatabase(tables: Array<TableDefinition>, version: Int): Database {
                return JDBCDatabase("jdbc:sqlite:").apply {
                    tables.forEach { execute(it.creationSql) }
                }
            }
        })
    }

    @After
    fun destroy() {
        localRepository.close()
    }

    @Test
    fun testGetUser() {
        localRepository.replaceAllUsers(listOf(person1, person2)).test()
        localRepository.getUser(person1.id).first().test {
            it.assertValue(person1)
        }
    }

    @Test
    fun testGetUsers() {
        localRepository.replaceAllUsers(listOf(person1, person2)).test()
        localRepository.getAllUsers().first().map { it.toSet() }.test {
            it.assertValue(setOf(person1, person2))
        }
    }

    @Test
    fun testSaveUser() {
        localRepository.saveUsers(person1).combineWith(localRepository.getUser(person1.id)).first().test {
            it.assertValue(person1.pairWith(person1))
        }
    }

    @Test
    fun testGetGroupMembers() {
        localRepository.replaceAllUsers(listOf(person1, person2))
                .flatMap { localRepository.replaceAllGroups(listOf(group1), groupMembers) }
                .flatMap { localRepository.getGroupMembers(group1.id) }
                .map { it.map { it.id }.toSet() }
                .first()
                .test {
                    it.assertValue(groupMembers[group1.id]!!.toSet())
                }
    }

    @Test
    fun testUpdateGroupMembers() {
        localRepository.replaceAllUsers(listOf(person1, person2))
                .flatMap { localRepository.replaceAllGroups(listOf(group1), hashMapOf()) }
                .flatMap { localRepository.updateGroupMembers(group1.id, groupMembers[group1.id]!!) }
                .flatMap { localRepository.getGroupMembers(group1.id) }
                .map { it.map { it.id }.toSet() }
                .first()
                .test {
                    it.assertValue(groupMembers[group1.id]!!.toSet())
                }
    }

    @Test
    fun testRooms() {
        localRepository.updateRoom(room1, roomMembers[room1.id]!!)
                .flatMap { localRepository.clearRooms() }
                .flatMap { localRepository.getRoom(room1.id) }
                .first()
                .test {
                    it.assertValue(null)
                }
    }
}