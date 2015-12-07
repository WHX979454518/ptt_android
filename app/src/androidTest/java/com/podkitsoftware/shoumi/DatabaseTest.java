package com.podkitsoftware.shoumi;

import android.support.v4.util.SimpleArrayMap;
import android.test.ApplicationTestCase;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.Person;

import junit.framework.Assert;

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
}
