package com.xianzhitech.ptt;

import android.support.v4.util.ArrayMap;
import android.test.AndroidTestCase;
import android.util.Log;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;
import com.xianzhitech.ptt.model.ContactItem;
import com.xianzhitech.ptt.model.Group;
import com.xianzhitech.ptt.model.Person;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by fanchao on 5/12/15.
 */
public class DatabaseTest extends AndroidTestCase {

    private static final Person PERSON_1 = new Person("1", "User 1", 0);
    private static final Person PERSON_2 = new Person("2", "User 2", 0);
    private static final Person PERSON_3 = new Person("3", "User 3", 0);
    private static final Person PERSON_4 = new Person("4", "User 4", 0);
    private static final Person PERSON_5 = new Person("5", "User 5", 0);
    private static final Group GROUP_1 = new Group("1", "Group 1");
    private static final Group GROUP_2 = new Group("2", "Group 2");
    private static final Group GROUP_3 = new Group("3", "Group 3");
    private static final Group GROUP_4 = new Group("4", "Group 4");

    private Broker broker;
    private Database db;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Log.d("DatabaseTest", "Creating test database");
        if (db != null) {
            throw new IllegalStateException();
        }
        db = new Database(getContext(), "testDb", Constants.DB_VERSION);
        broker = new Broker(db);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        Log.d("DatabaseTest", "Deleting test database");
        Closeables.close(db, true);
        getContext().deleteDatabase(db.name);
        db = null;
        broker = null;
    }

    public void testInsertGroup() throws Exception {
        final Group group = GROUP_3;
        final List<Group> groups = Collections.singletonList(group);
        broker.updateGroups(groups, new ArrayMap<>()).toBlocking().first();
        Assert.assertEquals(groups, broker.getGroups().toBlocking().first());
    }

    public void testReplaceGroup() throws Exception {
        broker.updateGroups(Arrays.asList(GROUP_4, GROUP_1), null).toBlocking().first();

        final Set<Group> secondGroupList = ImmutableSet.of(GROUP_2, GROUP_1);
        broker.updateGroups(secondGroupList, null).toBlocking().first();
        Assert.assertEquals(secondGroupList, ImmutableSet.copyOf(broker.getGroups().toBlocking().first()));
    }

    public void testInsertGroupMembers() {
        final List<Group> groups = Arrays.asList(GROUP_1, GROUP_2);
        broker.updateGroups(groups, null).toBlocking().first();

        final List<Person> persons = Arrays.asList(PERSON_1, PERSON_2);
        broker.updatePersons(persons).toBlocking().first();
        broker.addGroupMembers(groups.get(0), persons).toBlocking().first();

        Assert.assertEquals(persons, broker.getGroupMembers(groups.get(0).getId()).toBlocking().first());
    }

//    public void testReplaceMembers() {
//        final List<Person> persons = Arrays.asList(PERSON_1, PERSON_2, PERSON_3);
//        broker.updatePersons(persons).toBlocking().first();
//        Assert.assertEquals(persons, broker.getContacts(null).toBlocking().first());
//
//        final List<Person> secondPersonList = Arrays.asList(PERSON_4, PERSON_5);
//        broker.updatePersons(secondPersonList).toBlocking().first();
//        Assert.assertEquals(secondPersonList, broker.getContacts(null).toBlocking().first());
//    }

    public void testGetGroupWithMemberNames() {
        final List<Person> persons = Arrays.asList(PERSON_1, PERSON_2, PERSON_3);
        final ArrayList<String> personIds = new ArrayList<>(persons.size());
        final List<String> personNames = new ArrayList<>(persons.size());
        for (int i = 0, personsSize = persons.size(); i < personsSize; i++) {
            personIds.add(persons.get(i).getId());
            personNames.add(persons.get(i).getName());
        }

        final List<Group> groups = Arrays.asList(GROUP_1, GROUP_2);

        broker.updatePersons(persons).toBlocking().first();
        final ArrayMap<String, List<String>> groupMembers = new ArrayMap<>();
        groupMembers.put(groups.get(0).getId(), personIds);
        broker.updateGroups(groups, groupMembers).toBlocking().first();

        List<Broker.AggregateInfo<Group, String>> result = broker.getGroupsWithMemberNames(1).toBlocking().first();
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(groups.get(0), result.get(0).group);
        Assert.assertEquals(groups.get(1), result.get(1).group);
        Assert.assertEquals(personNames.subList(0, 1), result.get(0).members);
        Assert.assertEquals(3, result.get(0).memberCount);

        result = broker.getGroupsWithMemberNames(100).toBlocking().first();
        Assert.assertEquals(groups.get(0), result.get(0).group);
        Assert.assertEquals(personNames, result.get(0).members);
        Assert.assertEquals(3, result.get(0).memberCount);
    }

    public void testContact() {
        final ImmutableSet<Person> persons = ImmutableSet.of(PERSON_1, PERSON_2, PERSON_3);
        final ImmutableSet<Group> groups = ImmutableSet.of(GROUP_1);
        final ArrayMap<String, List<String>> groupMembers = new ArrayMap<>();
        groupMembers.put(GROUP_1.getId(), Arrays.asList(PERSON_1.getId(), PERSON_3.getId()));

        broker.updatePersons(persons).toBlocking().first();
        broker.updateGroups(groups, groupMembers).toBlocking().first();

        broker.updateContacts(Iterables.transform(persons, Person::getId), Iterables.transform(groups, Group::getId)).toBlocking().first();

        final ImmutableSet<ContactItem> expectedContacts = ImmutableSet.of(PERSON_1, PERSON_2, PERSON_3, GROUP_1);
        assertEquals(expectedContacts, ImmutableSet.copyOf(broker.getContacts(null).toBlocking().first()));
    }
}
