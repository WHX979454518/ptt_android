package com.podkitsoftware.shoumi;

import android.support.v4.util.SimpleArrayMap;
import android.test.AndroidTestCase;
import android.util.Log;

import com.google.common.io.Closeables;
import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupInfo;
import com.podkitsoftware.shoumi.model.Person;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by fanchao on 5/12/15.
 */
public class DatabaseTest extends AndroidTestCase {

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
        if (!getContext().deleteDatabase(db.name)) {
            throw new IllegalStateException("Can't delete database: " + db.name);
        }
        db = null;
        broker = null;
    }

    public void testInsertGroup() throws Exception {
        final Group group = new Group("12345", "My Group");
        final List<Group> groups = Collections.singletonList(group);
        broker.updateGroups(groups, new SimpleArrayMap<>()).toBlocking().first();
        Assert.assertEquals(groups, broker.getGroups().toBlocking().first());
    }

    public void testReplaceGroup() throws Exception {
        final List<Group> groups = Arrays.asList(new Group("12345", "Group 1"), new Group("12346", "Group 2"));
        broker.updateGroups(groups, null).toBlocking().first();

        final List<Group> secondGroupList = Arrays.asList(new Group("1", "Group 3"), new Group("2", "Group 4"));
        broker.updateGroups(secondGroupList, null).toBlocking().first();
        Assert.assertEquals(secondGroupList, broker.getGroups().toBlocking().first());
    }

    public void testInsertGroupMembers() {
        final List<Group> groups = Arrays.asList(new Group("1", "Group 1"), new Group("2", "Group 2"));
        broker.updateGroups(groups, null).toBlocking().first();

        final List<Person> persons = Arrays.asList(new Person("1", "User 1", true), new Person("2", "User 2", true));
        broker.updatePersons(persons).toBlocking().first();
        broker.addGroupMembers(groups.get(0), persons).toBlocking().first();

        Assert.assertEquals(persons, broker.getGroupMembers(groups.get(0).getId()).toBlocking().first());
    }

    public void testReplaceMembers() {
        final List<Person> persons = Arrays.asList(new Person("2", "User 1", true), new Person("3", "User 2", true), new Person("4", "User 3", true));
        broker.updatePersons(persons).toBlocking().first();
        Assert.assertEquals(persons, broker.getContacts(null).toBlocking().first());

        final List<Person> secondPersonList = Arrays.asList(new Person("4", "User 1", true), new Person("5", "User 2", true));
        broker.updatePersons(secondPersonList).toBlocking().first();
        Assert.assertEquals(secondPersonList, broker.getContacts(null).toBlocking().first());
    }

    public void testGetGroupWithMemberNames() {
        final List<Person> persons = Arrays.asList(new Person("0", "User 1", true), new Person("1", "User 2", true), new Person("2", "User 3", true));
        final ArrayList<String> personIds = new ArrayList<>(persons.size());
        final List<String> personNames = new ArrayList<>(persons.size());
        for (int i = 0, personsSize = persons.size(); i < personsSize; i++) {
            personIds.add(persons.get(i).getId());
            personNames.add(persons.get(i).getName());
        }

        final List<Group> groups = Arrays.asList(new Group("0", "Group 1"), new Group("1", "Group 2"));

        broker.updatePersons(persons).toBlocking().first();
        final SimpleArrayMap<Group, List<String>> groupMembers = new SimpleArrayMap<>();
        groupMembers.put(groups.get(0), personIds);
        broker.updateGroups(groups, groupMembers).toBlocking().first();

        List<GroupInfo<String>> result = broker.getGroupsWithMemberNames(1).toBlocking().first();
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
}
