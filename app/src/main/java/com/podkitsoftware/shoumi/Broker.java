package com.podkitsoftware.shoumi;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import android.support.v4.util.SimpleArrayMap;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupInfo;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.Person;
import com.podkitsoftware.shoumi.util.CursorUtil;
import com.podkitsoftware.shoumi.util.SqlUtil;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.QueryObservable;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public enum Broker {
    INSTANCE;

    final Scheduler queryScheduler = Schedulers.computation();
    final Scheduler modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor());

    final BehaviorSubject<Set<String>> onlineUsersSubject = BehaviorSubject.create(Collections.emptySet());

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

    public void updateOnlineUsers(final Collection<String> newOnlineUsers) {
        onlineUsersSubject.onNext(Collections.unmodifiableSet(new HashSet<>(newOnlineUsers)));
    }

    @CheckResult
    public Observable<Set<String>> getOnlineUsers() {
        return onlineUsersSubject;
    }

    @CheckResult
    public Observable<Group> getGroup(final String id) {
        return Database.INSTANCE
                .createQuery(Group.TABLE_NAME, "SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.COL_ID + " = ?", String.valueOf(id))
                .mapToOne(Group.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<List<Group>> getGroups() {
        return Database.INSTANCE
                .createQuery(Group.TABLE_NAME, "SELECT * FROM " + Group.TABLE_NAME)
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
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
                                group.getId()), 0);

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
    public Observable<List<Person>> getGroupMembers(final String groupId) {
        final String sql = "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " +
                "LEFT JOIN " + GroupMember.TABLE_NAME + " AS GM ON " +
                "GM." + GroupMember.COL_PERSON_ID + " = P." + Person.COL_ID + " " +
                "WHERE GM." + GroupMember.COL_GROUP_ID + " = ?";


        return Database.INSTANCE
                .createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME), sql, String.valueOf(groupId))
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<Void> updateGroups(final List<Group> groups, final SimpleArrayMap<Group, String[]> groupMembers) {
        return updateInTransaction(() -> {
            final String[] groupIds;

            // Replace all existing groups
            if (groups != null && !groups.isEmpty()) {
                groupIds = new String[groups.size()];
                final ContentValues contentValues = new ContentValues();
                for (int i = 0, groupsSize = groups.size(); i < groupsSize; i++) {
                    final Group group = groups.get(i);
                    groupIds[i] = group.getId();
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
    public Observable<Void> updateGroupMembers(final Group group, final String[] groupMembers) {
        return updateInTransaction(() -> {
            doUpdateGroupMembers(group.getId(), groupMembers);
            return null;
        });
    }

    @CheckResult
    public Observable<List<Person>> getContacts(final @Nullable String searchTerm) {
        final QueryObservable query;
        if (StringUtils.isNotEmpty(searchTerm)) {
            query = Database.INSTANCE
                    .createQuery(Person.TABLE_NAME,
                            "SELECT * FROM " + Person.TABLE_NAME + " " +
                                    "WHERE " + Person.COL_IS_CONTACT + " <> 0 AND " + Person.COL_NAME + " LIKE ?", '%' + searchTerm + '%');
        }
        else {
            query = Database.INSTANCE
                    .createQuery(Person.TABLE_NAME,
                            "SELECT * FROM " + Person.TABLE_NAME + " WHERE " + Person.COL_IS_CONTACT + " <> 0");
        }

        return query
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<Void> addGroupMembers(final Group group, final List<Person> persons) {
        if (persons == null || persons.isEmpty()) {
            return Observable.just(null);
        }

        return updateInTransaction(() -> {
            final String[] members = new String[persons.size()];
            for (int i = 0, personsSize = persons.size(); i < personsSize; i++) {
                members[i] = persons.get(i).getId();
            }
            doAddGroupMembers(group.getId(), members);
            return null;
        });
    }

    @CheckResult
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

    private static void doAddGroupMembers(String groupId, String[] members) {
        final ContentValues values = new ContentValues(2);

        for (String memberId : members) {
            values.put(GroupMember.COL_GROUP_ID, groupId);
            values.put(GroupMember.COL_PERSON_ID, memberId);
            Database.INSTANCE.insert(GroupMember.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void doUpdateGroupMembers(String groupId, String[] members) {
        doAddGroupMembers(groupId, members);

        Database.INSTANCE.delete(GroupMember.TABLE_NAME,
                GroupMember.COL_GROUP_ID + " = ? AND " +
                GroupMember.COL_PERSON_ID + " NOT IN " + SqlUtil.toSqlSet(members), groupId);
    }

}
