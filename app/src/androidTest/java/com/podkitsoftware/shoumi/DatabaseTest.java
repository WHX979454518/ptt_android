package com.podkitsoftware.shoumi;

import android.support.v4.util.SimpleArrayMap;
import android.test.ApplicationTestCase;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupInfo;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.Person;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by fanchao on 5/12/15.
 */
public class DatabaseTest extends ApplicationTestCase<App> {

    public DatabaseTest() {
        super(App.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Database.INSTANCE.delete(Person.TABLE_NAME, "1");
        Database.INSTANCE.delete(Group.TABLE_NAME, "1");
        Database.INSTANCE.delete(GroupMember.TABLE_NAME, "1");
    }

    public void testInsertGroup() throws Exception {
        final Group group = new Group(12345, "My Group");
        final List<Group> groups = Collections.singletonList(group);
        Broker.INSTANCE.updateGroups(groups, new SimpleArrayMap<>()).toBlocking().first();
        Assert.assertEquals(groups, Broker.INSTANCE.getGroups().toBlocking().first());
    }

    public void testReplaceGroup() throws Exception {
        final List<Group> groups = Arrays.asList(new Group(12345, "Group 1"), new Group(12346, "Group 2"));
        Broker.INSTANCE.updateGroups(groups, null).toBlocking().first();

        final List<Group> secondGroupList = Arrays.asList(new Group(1, "Group 3"), new Group(2, "Group 4"));
        Broker.INSTANCE.updateGroups(secondGroupList, null).toBlocking().first();
        Assert.assertEquals(secondGroupList, Broker.INSTANCE.getGroups().toBlocking().first());
    }

    public void testInsertGroupMembers() {
        final List<Group> groups = Arrays.asList(new Group(1, "Group 1"), new Group(2, "Group 2"));
        Broker.INSTANCE.updateGroups(groups, null).toBlocking().first();

        final List<Person> persons = Arrays.asList(new Person(1, "User 1"), new Person(2, "User 2"));
        Broker.INSTANCE.updatePersons(persons).toBlocking().first();
        Broker.INSTANCE.addGroupMembers(groups.get(0), persons).toBlocking().first();

        Assert.assertEquals(persons, Broker.INSTANCE.getGroupMembers(groups.get(0)).toBlocking().first());
    }

    public void testReplaceMembers() {
        final List<Person> persons = Arrays.asList(new Person(2, "User 1"), new Person(3, "User 2"), new Person(4, "User 3"));
        Broker.INSTANCE.updatePersons(persons).toBlocking().first();
        Assert.assertEquals(persons, Broker.INSTANCE.getPersons().toBlocking().first());

        final List<Person> secondPersonList = Arrays.asList(new Person(4, "User 1"), new Person(5, "User 2"));
        Broker.INSTANCE.updatePersons(secondPersonList).toBlocking().first();
        Assert.assertEquals(secondPersonList, Broker.INSTANCE.getPersons().toBlocking().first());
    }

    public void testGetGroupWithMemberNames() {
        final List<Person> persons = Arrays.asList(new Person(0, "User 1"), new Person(1, "User 2"), new Person(2, "User 3"));
        final long[] personIds = new long[persons.size()];
        final List<String> personNames = new ArrayList<>(persons.size());
        for (int i = 0, personsSize = persons.size(); i < personsSize; i++) {
            personIds[i] = persons.get(i).getId();
            personNames.add(persons.get(i).getName());
        }

        final List<Group> groups = Arrays.asList(new Group(0, "Group 1"), new Group(1, "Group 2"));

        Broker.INSTANCE.updatePersons(persons).toBlocking().first();
        final SimpleArrayMap<Group, long[]> groupMembers = new SimpleArrayMap<>();
        groupMembers.put(groups.get(0), personIds);
        Broker.INSTANCE.updateGroups(groups, groupMembers).toBlocking().first();

        List<GroupInfo<String>> result = Broker.INSTANCE.getGroupsWithMemberNames(1).toBlocking().first();
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(groups.get(0), result.get(0).group);
        Assert.assertEquals(groups.get(1), result.get(1).group);
        Assert.assertEquals(personNames.subList(0, 1), result.get(0).members);
        Assert.assertEquals(3, result.get(0).memberCount);

        result = Broker.INSTANCE.getGroupsWithMemberNames(100).toBlocking().first();
        Assert.assertEquals(groups.get(0), result.get(0).group);
        Assert.assertEquals(personNames, result.get(0).members);
        Assert.assertEquals(3, result.get(0).memberCount);
    }
}
