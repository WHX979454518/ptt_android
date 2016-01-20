package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.db.Database
import com.xianzhitech.ptt.db.JDBCDatabase
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.pairWith
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.util.test
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
    private lateinit var db: Database
    private lateinit var localRepository: LocalRepository

    private val person1 = User("1", "hello", EnumSet.allOf(Privilege::class.java))
    private val person2 = User("2", "hello2", EnumSet.allOf(Privilege::class.java))
    private val group1 = Group("1", "hello1")
    private val room1 = Room("1", "Room1", "", person1.id, false)
    private val groupMembers = hashMapOf(Pair(group1.id, listOf(person1.id, person2.id)))
    private val roomMembers = hashMapOf(Pair(room1.id, listOf(person1.id, person2.id)))

    @Before
    fun init() {
        db = JDBCDatabase("jdbc:sqlite:").apply {
            execute("DROP TABLE IF EXISTS ${User.TABLE_NAME}")
            execute("DROP TABLE IF EXISTS ${Group.TABLE_NAME}")
            execute("DROP TABLE IF EXISTS ${GroupMembers.TABLE_NAME}")
            execute("DROP TABLE IF EXISTS ${Room.TABLE_NAME}")
            execute("DROP TABLE IF EXISTS ${RoomMembers.TABLE_NAME}")
            execute("DROP TABLE IF EXISTS ${Contacts.TABLE_NAME}")

            execute(User.CREATE_TABLE_SQL)
            execute(Group.CREATE_TABLE_SQL)
            execute(GroupMembers.CREATE_TABLE_SQL)
            execute(Room.CREATE_TABLE_SQL)
            execute(RoomMembers.CREATE_TABLE_SQL)
            execute(Contacts.CREATE_TABLE_SQL)
        }
        localRepository = LocalRepository(db)
    }

    @After
    fun destroy() {
        db.close()
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
        localRepository.saveUser(person1).combineWith(localRepository.getUser(person1.id)).first().test {
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