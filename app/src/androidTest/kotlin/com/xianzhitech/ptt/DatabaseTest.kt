package com.xianzhitech.ptt

import android.support.v4.util.ArrayMap
import android.test.AndroidTestCase
import android.util.Log
import com.xianzhitech.ptt.ext.toBlockingFirst
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.service.MockConversations
import com.xianzhitech.ptt.service.MockGroups
import com.xianzhitech.ptt.service.MockPersons
import junit.framework.Assert
import java.util.*

/**
 * Created by fanchao on 5/12/15.
 */
class DatabaseTest : AndroidTestCase() {

    private lateinit var broker: Broker
    private lateinit var db: Database

    override fun setUp() {
        super.setUp()

        Log.d("DatabaseTest", "Creating test database")
        db = Database(context, "testDb", Constants.DB_VERSION)
        broker = Broker(db)
    }

    override fun tearDown() {
        super.tearDown()

        Log.d("DatabaseTest", "Deleting test database")
        db.close()
        context.deleteDatabase(db.name)
    }

    fun testInsertGroup() {
        val groups = listOf(MockGroups.GROUP_3)
        broker.updateAllGroups(groups, emptyMap()).toBlocking().first()
        Assert.assertEquals(groups, broker.groups.toBlocking().first())
    }

    fun testReplaceGroup() {
        broker.updateAllGroups(listOf(MockGroups.GROUP_4, MockGroups.GROUP_1), null).toBlocking().first()

        val secondGroupList = setOf(MockGroups.GROUP_2, MockGroups.GROUP_1)
        broker.updateAllGroups(secondGroupList, null).toBlocking().first()
        Assert.assertEquals(secondGroupList, broker.groups.toBlocking().first().toSet())
    }

    fun testInsertGroupMembers() {
        val groups = listOf(MockGroups.GROUP_1, MockGroups.GROUP_2)
        broker.updateAllGroups(groups, null).toBlocking().first()

        val persons = listOf(MockPersons.PERSON_1, MockPersons.PERSON_2)
        broker.updateAllPersons(persons).toBlocking().first()
        broker.addGroupMembers(groups[0], persons).toBlocking().first()

        Assert.assertEquals(persons, broker.getGroupMembers(groups[0].id).toBlocking().first())
    }

    //    public void testReplaceMembers() {
    //        final List<Person> persons = listOf(MockPersons.PERSON_1, MockPersons.PERSON_2, MockPersons.PERSON_3);
    //        broker.updatePersons(persons).toBlocking().first();
    //        Assert.assertEquals(persons, broker.getContacts(null).toBlocking().first());
    //
    //        final List<Person> secondPersonList = listOf(MockPersons.PERSON_4, MockPersons.PERSON_5);
    //        broker.updatePersons(secondPersonList).toBlocking().first();
    //        Assert.assertEquals(secondPersonList, broker.getContacts(null).toBlocking().first());
    //    }

    fun testGetGroupWithMemberNames() {
        val persons = listOf(MockPersons.PERSON_1, MockPersons.PERSON_2, MockPersons.PERSON_3)
        val personIds = ArrayList<String>(persons.size)
        val personNames = ArrayList<String>(persons.size)
        var i = 0
        val personsSize = persons.size
        while (i < personsSize) {
            personIds.add(persons[i].id)
            personNames.add(persons[i].name)
            i++
        }

        val groups = listOf(MockGroups.GROUP_1, MockGroups.GROUP_2)

        broker.updateAllPersons(persons).toBlocking().first()
        val groupMembers = ArrayMap<String, List<String>>()
        groupMembers.put(groups[0].id, personIds)
        broker.updateAllGroups(groups, groupMembers).toBlocking().first()

        var result: List<Broker.AggregateInfo<Group, String>> = broker.getGroupsWithMemberNames(1).toBlocking().first()
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(groups[0], result[0].group)
        Assert.assertEquals(groups[1], result[1].group)
        Assert.assertEquals(personNames.subList(0, 1), result[0].members)
        Assert.assertEquals(3, result[0].memberCount)

        result = broker.getGroupsWithMemberNames(100).toBlocking().first()
        Assert.assertEquals(groups[0], result[0].group)
        Assert.assertEquals(personNames, result[0].members)
        Assert.assertEquals(3, result[0].memberCount)
    }

    fun testContact() {
        val persons = setOf(MockPersons.PERSON_1, MockPersons.PERSON_2, MockPersons.PERSON_3)
        val groups = setOf(MockGroups.GROUP_1)
        val groupMembers = ArrayMap<String, List<String>>()
        groupMembers.put(MockGroups.GROUP_1.id, listOf(MockPersons.PERSON_1.id, MockPersons.PERSON_3.id))

        broker.updateAllPersons(persons).toBlocking().first()
        broker.updateAllGroups(groups, groupMembers).toBlocking().first()

        broker.updateAllContacts(persons.transform { it.id }, groups.transform { it.id }).toBlocking().first()

        val expectedContacts = setOf(MockPersons.PERSON_1, MockPersons.PERSON_2, MockPersons.PERSON_3, MockGroups.GROUP_1)
        Assert.assertEquals(expectedContacts, broker.getContacts(null).toBlocking().first().toSet())
    }

    fun testConversation() {
        MockConversations.ALL.forEach {
            broker.updateAllPersons(MockPersons.ALL).toBlockingFirst()
            broker.saveConversation(it).toBlockingFirst()
            broker.updateConversationMembers(it.id, MockConversations.CONVERSATION_MEMBERS[it.id] ?: emptyList()).toBlockingFirst()
        }

        MockConversations.CONVERSATION_1.apply {
            assertEquals(this, broker.getConversation(id).toBlockingFirst())
            assertEquals(MockConversations.CONVERSATION_MEMBERS[id]?.toSet(),
                    broker.getConversationMembers(id).toBlockingFirst().transform { it.id }.toSet())
        }
    }
}
