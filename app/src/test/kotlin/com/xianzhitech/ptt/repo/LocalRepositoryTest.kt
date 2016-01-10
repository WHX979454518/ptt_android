package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.db.Database
import com.xianzhitech.ptt.db.JDBCDatabase
import com.xianzhitech.ptt.ext.toBlockingFirst
import com.xianzhitech.ptt.model.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import java.util.*
import kotlin.collections.listOf
import kotlin.collections.toSet

/**
 * Created by fanchao on 10/01/16.
 */
@RunWith(MockitoJUnitRunner::class)
class LocalRepositoryTest {
    private lateinit var db: Database
    private lateinit var localRepository: LocalRepository

    private val person1 = Person("1", "hello", EnumSet.allOf(Privilege::class.java))
    private val person2 = Person("2", "hello2", EnumSet.allOf(Privilege::class.java))

    @Before
    fun init() {
        db = JDBCDatabase("jdbc:sqlite:memory:").apply {
            execute("DROP TABLE ${Person.TABLE_NAME}")
            execute("DROP TABLE ${Group.TABLE_NAME}")
            execute("DROP TABLE ${GroupMembers.TABLE_NAME}")
            execute("DROP TABLE ${Conversation.TABLE_NAME}")
            execute("DROP TABLE ${ConversationMembers.TABLE_NAME}")
            execute("DROP TABLE ${Contacts.TABLE_NAME}")

            execute(Person.CREATE_TABLE_SQL)
            execute(Group.CREATE_TABLE_SQL)
            execute(GroupMembers.CREATE_TABLE_SQL)
            execute(Conversation.CREATE_TABLE_SQL)
            execute(ConversationMembers.CREATE_TABLE_SQL)
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
        localRepository.replaceAllUsers(listOf(person1, person2)).toBlockingFirst()
        Assert.assertEquals(person1, localRepository.getUser(person1.id).toBlockingFirst())
    }


    @Test
    fun testReplaceAllUsers() {
        val userList = listOf(person1, person2)
        localRepository.replaceAllUsers(userList).toBlockingFirst()
        Assert.assertEquals(userList.toSet(), localRepository.getAllUsers().toBlockingFirst().toSet())
    }

}