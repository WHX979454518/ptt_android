package com.podkitsoftware.shoumi;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.CheckResult;
import android.support.v4.util.SimpleArrayMap;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupInfo;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.Person;
import com.podkitsoftware.shoumi.util.CursorUtil;
import com.podkitsoftware.shoumi.util.SqlUtil;
import com.squareup.sqlbrite.BriteDatabase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

public enum Broker {
    INSTANCE;

    final Scheduler queryScheduler = Schedulers.computation();
    final Scheduler modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor());

    private <T> Observable<T> updateInTransaction(final Func0<T> func) {
        return Observable.defer(() -> {
            final BriteDatabase.Transaction transaction = Database.INSTANCE.newTransaction();
            try {
                final T result = func.call();
                transaction.markSuccessful();
                return Observable.just(result);
            } finally {
                transaction.close();
            }
        }).subscribeOn(modifyScheduler);
    }

    @CheckResult
    public Observable<List<Group>> getGroups() {
        return Database.INSTANCE
                .createQuery(Group.TABLE_NAME, "SELECT * FROM " + Group.TABLE_NAME)
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler);
    }

    public Observable<List<GroupInfo<String>>> getGroupsWithMemberNames(int maxMember) {
        return Database.INSTANCE
                .createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME, GroupMember.TABLE_NAME),
                        "SELECT * FROM " + Group.TABLE_NAME)
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler)
                .map(groups -> {
                    for (int i = 0, groupsSize = groups.size(); i < groupsSize; i++) {
                        final Group group = groups.get(i);

                        // Count group member
                        final int memberCount = CursorUtil.countAndClose(Database.INSTANCE.query(
                                "SELECT COUNT(" + GroupMember.COL_PERSON_ID + ") FROM " + GroupMember.TABLE_NAME + " WHERE " + GroupMember.COL_GROUP_ID + " = ?",
                                Long.toString(group.getId())), 0);

                        // Get members
                        final List<String> persons = CursorUtil.mapCursorAndClose(Database.INSTANCE.query("SELECT P." + Person.COL_NAME + " FROM " + Person.TABLE_NAME + " AS P " +
                                        "INNER JOIN " + GroupMember.TABLE_NAME + " AS GM ON GM." + GroupMember.COL_PERSON_ID + " = P." + Person.COL_ID + " AND " + GroupMember.COL_GROUP_ID + " = ? " +
                                        "LIMIT " + maxMember,
                                String.valueOf(group.getId())), cursor -> cursor.getString(0));

                        ((List) groups).set(i, new GroupInfo<>(group, Collections.unmodifiableList(persons), memberCount));
                    }
                    return (List) groups;
                });
    }

    @CheckResult
    public Observable<List<Person>> getGroupMembers(final Group group) {
        final String sql = "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " +
                "LEFT JOIN " + GroupMember.TABLE_NAME + " AS GM ON " +
                "GM." + GroupMember.COL_PERSON_ID + " = P." + Person.COL_ID + " " +
                "WHERE GM." + GroupMember.COL_GROUP_ID + " = ?";


        return Database.INSTANCE
                .createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME), sql, Long.toString(group.getId()))
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<Void> updateGroups(final List<Group> groups, final SimpleArrayMap<Group, long[]> groupMembers) {
        return updateInTransaction(() -> {
            final String[] groupIds;

            // Replace all existing groups
            if (groups != null && !groups.isEmpty()) {
                groupIds = new String[groups.size()];
                final ContentValues contentValues = new ContentValues();
                for (int i = 0, groupsSize = groups.size(); i < groupsSize; i++) {
                    final Group group = groups.get(i);
                    groupIds[i] = Long.toString(group.getId());
                    contentValues.clear();
                    group.toValues(contentValues);
                    Database.INSTANCE.insert(Group.TABLE_NAME, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
                }
            } else {
                groupIds = null;
            }

            // Delete remaining groups.
            Database.INSTANCE.delete(Group.TABLE_NAME,
                    Group.COL_ID + " NOT IN " + SqlUtil.toSqlSet(groupIds));

            // Replace all group members
            if (groupMembers != null) {
                for (int i = 0, size = groupMembers.size(); i < size; i++) {
                    doUpdateGroupMembers(groupMembers.keyAt(i).getId(), groupMembers.valueAt(i));
                }
            }

            return null;
        });
    }

    @CheckResult
    public Observable<Void> updateGroupMembers(final Group group, final long[] groupMembers) {
        return updateInTransaction(() -> {
            doUpdateGroupMembers(group.getId(), groupMembers);
            return null;
        });
    }

    public Observable<List<Person>> getPersons() {
        return Database.INSTANCE
                .createQuery(Person.TABLE_NAME,  "SELECT * FROM " + Person.TABLE_NAME)
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler);
    }

    public Observable<Void> addGroupMembers(final Group group, final List<Person> persons) {
        if (persons == null || persons.isEmpty()) {
            return Observable.just(null);
        }

        return updateInTransaction(() -> {
            final long[] members = new long[persons.size()];
            for (int i = 0, personsSize = persons.size(); i < personsSize; i++) {
                members[i] = persons.get(i).getId();
            }
            doAddGroupMembers(group.getId(), members);
            return null;
        });
    }

    public Observable<Void> updatePersons(final Collection<Person> persons) {
        return updateInTransaction(() -> {
            Database.INSTANCE.delete(Person.TABLE_NAME, "");


            final ContentValues values = new ContentValues();
            for (Person person : persons) {
                values.clear();
                person.toValues(values);
                Database.INSTANCE.insert(Person.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            return null;
        });
    }

    private static void doAddGroupMembers(long groupId, long[] members) {
        final ContentValues values = new ContentValues(2);

        for (long memberId : members) {
            values.put(GroupMember.COL_GROUP_ID, groupId);
            values.put(GroupMember.COL_PERSON_ID, memberId);
            Database.INSTANCE.insert(GroupMember.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void doUpdateGroupMembers(long groupId, long[] members) {
        doAddGroupMembers(groupId, members);

        Database.INSTANCE.delete(GroupMember.TABLE_NAME,
                GroupMember.COL_GROUP_ID + " = ? AND " +
                GroupMember.COL_PERSON_ID + " NOT IN " + SqlUtil.toSqlSet(members), String.valueOf(groupId));
    }

}
