package com.xianzhitech.ptt;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;

import com.google.common.collect.Iterables;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.QueryObservable;
import com.xianzhitech.ptt.model.ContactItem;
import com.xianzhitech.ptt.model.Contacts;
import com.xianzhitech.ptt.model.Conversation;
import com.xianzhitech.ptt.model.ConversationMembers;
import com.xianzhitech.ptt.model.Group;
import com.xianzhitech.ptt.model.GroupMembers;
import com.xianzhitech.ptt.model.Person;
import com.xianzhitech.ptt.util.CursorUtil;
import com.xianzhitech.ptt.util.SqlUtil;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class Broker {

    final Scheduler queryScheduler = Schedulers.computation();
    final Scheduler modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    final Database db;

    final BehaviorSubject<Set<String>> onlineUsersSubject = BehaviorSubject.create(Collections.emptySet());

    public Broker(final Database db) {
        this.db = db;
    }

    private <T> Observable<T> updateInTransaction(final Func0<T> func) {
        return Observable.defer(() -> {
            final BriteDatabase.Transaction transaction = db.newTransaction();
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
        return db
                .createQuery(Group.TABLE_NAME, "SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.COL_ID + " = ?", id)
                .mapToOne(Group.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<List<Group>> getGroups() {
        return db
                .createQuery(Group.TABLE_NAME, "SELECT * FROM " + Group.TABLE_NAME)
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<List<AggregateInfo<Group, String>>> getGroupsWithMemberNames(int maxMember) {
        return db
                .createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME, GroupMembers.TABLE_NAME),
                        "SELECT * FROM " + Group.TABLE_NAME)
                .mapToList(Group.MAPPER)
                .subscribeOn(queryScheduler)
                .map(groups -> {
                    for (int i = 0, groupsSize = groups.size(); i < groupsSize; i++) {
                        final Group group = groups.get(i);

                        // Count group member
                        final int memberCount = CursorUtil.countAndClose(db.query(
                                "SELECT COUNT(" + GroupMembers.COL_PERSON_ID + ") FROM " + GroupMembers.TABLE_NAME + " WHERE " + GroupMembers.COL_GROUP_ID + " = ?",
                                group.getId()), 0);

                        // Get members
                        final List<String> persons = CursorUtil.mapCursorAndClose(db.query("SELECT P." + Person.COL_NAME + " FROM " + Person.TABLE_NAME + " AS P " +
                                        "INNER JOIN " + GroupMembers.TABLE_NAME + " AS GM ON GM." + GroupMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " AND " + GroupMembers.COL_GROUP_ID + " = ? " +
                                        "LIMIT " + maxMember,
                                group.getId()), cursor -> cursor.getString(0));

                        ((List) groups).set(i, new AggregateInfo<>(group, Collections.unmodifiableList(persons), memberCount));
                    }
                    return (List) groups;
                });
    }

    @CheckResult
    public Observable<List<AggregateInfo<Conversation, String>>> getConversationsWithMemberNames(int maxMember) {
        return db
                .createQuery(Arrays.asList(Conversation.TABLE_NAME, ConversationMembers.TABLE_NAME),
                        "SELECT * FROM " + Conversation.TABLE_NAME)
                .mapToList(Conversation.MAPPER)
                .subscribeOn(queryScheduler)
                .map(conversations -> {
                    for (int i = 0, groupsSize = conversations.size(); i < groupsSize; i++) {
                        final Conversation conversation = conversations.get(i);

                        // Count member
                        final int memberCount = CursorUtil.countAndClose(db.query(
                                "SELECT COUNT(" + ConversationMembers.COL_PERSON_ID + ") FROM " + ConversationMembers.TABLE_NAME + " WHERE " + ConversationMembers.COL_CONVERSATION_ID + " = ?",
                                conversation.getId()), 0);

                        // Get members
                        final List<String> persons = CursorUtil.mapCursorAndClose(db.query("SELECT P." + Person.COL_NAME + " FROM " + Person.TABLE_NAME + " AS P " +
                                        "INNER JOIN " + ConversationMembers.TABLE_NAME + " AS GM ON GM." + ConversationMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " AND " + ConversationMembers.COL_CONVERSATION_ID + " = ? " +
                                        "LIMIT " + maxMember,
                                conversation.getId()), cursor -> cursor.getString(0));

                        ((List) conversations).set(i, new AggregateInfo<>(conversation, Collections.unmodifiableList(persons), memberCount));
                    }
                    return (List) conversations;
                });
    }

    @CheckResult
    public Observable<List<Person>> getGroupMembers(final String groupId) {
        final String sql = "SELECT P.* FROM " + Person.TABLE_NAME + " AS P " +
                "LEFT JOIN " + GroupMembers.TABLE_NAME + " AS GM ON " +
                "GM." + GroupMembers.COL_PERSON_ID + " = P." + Person.COL_ID + " " +
                "WHERE GM." + GroupMembers.COL_GROUP_ID + " = ?";


        return db
                .createQuery(Arrays.asList(Group.TABLE_NAME, Person.TABLE_NAME), sql, groupId)
                .mapToList(Person.MAPPER)
                .subscribeOn(queryScheduler);
    }

    @CheckResult
    public Observable<Void> updateGroups(final Iterable<Group> groups, final Map<String, ? extends Iterable<String>> groupMembers) {
        return updateInTransaction(() -> {

            // Replace all existing groups
            if (groups != null) {
                final ContentValues contentValues = new ContentValues();
                int i = 0;
                for (final Group group : groups) {
                    contentValues.clear();
                    group.toValues(contentValues);
                    db.insert(Group.TABLE_NAME, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            // Delete remaining groups.
            if (groups == null) {
                db.delete(Group.TABLE_NAME, "1");
            }
            else {
                db.delete(Group.TABLE_NAME, Group.COL_ID + " NOT IN " + SqlUtil.toSqlSet(Iterables.transform(groups, Group::getId)));
            }

            // Replace all group members
            if (groupMembers != null) {
                for (Map.Entry<String, ? extends Iterable<String>> entry : groupMembers.entrySet()) {
                    doUpdateGroupMembers(entry.getKey(), entry.getValue());
                }
            }

            return null;
        });
    }

    @CheckResult
    public Observable<Void> updateGroupMembers(final String groupId, final Iterable<String> groupMembers) {
        return updateInTransaction(() -> {
            doUpdateGroupMembers(groupId, groupMembers);
            return null;
        });
    }

    @CheckResult
    public Observable<List<ContactItem>> getContacts(final @Nullable String searchTerm) {
        final QueryObservable query;
        if (StringUtils.isNotEmpty(searchTerm)) {
            final String formattedSearchTerm = '%' + searchTerm + '%';
            query = db
                    .createQuery(Contacts.TABLE_NAME,
                            "SELECT * FROM " + Contacts.TABLE_NAME + " AS CI " +
                                    "LEFT JOIN " + Person.TABLE_NAME + " AS P ON CI." + Contacts.COL_PERSON_ID + " = P." + Person.COL_ID + " " +
                                    "LEFT JOIN " + Group.TABLE_NAME + " AS G ON CI." + Contacts.COL_GROUP_ID + " = G." + Group.COL_ID + " " +
                                    "WHERE (P." + Person.COL_NAME + " LIKE ? OR G." + Group.COL_NAME + " LIKE ? )",
                            formattedSearchTerm,
                            formattedSearchTerm);
        }
        else {
            query = db
                    .createQuery(Contacts.TABLE_NAME,
                            "SELECT * FROM " + Contacts.TABLE_NAME + " AS CI " +
                                    "LEFT JOIN " + Person.TABLE_NAME + " AS P ON CI." + Contacts.COL_PERSON_ID + " = P." + Person.COL_ID + " " +
                                    "LEFT JOIN " + Group.TABLE_NAME + " AS G ON CI." + Contacts.COL_GROUP_ID + " = G." + Group.COL_ID);

        }

        return query.mapToList(Contacts.MAPPER).subscribeOn(queryScheduler);
    }


    @CheckResult
    public Observable<Void> updateContacts(final @Nullable Iterable<String> persons, final @Nullable Iterable<String> groups) {
        return updateInTransaction(() -> {
            db.execute("DELETE FROM " + Contacts.TABLE_NAME + " WHERE 1");
            final ContentValues values = new ContentValues();
            if (persons != null) {
                for (final String personId : persons) {
                    values.clear();
                    values.put(Contacts.COL_PERSON_ID, personId);
                    db.insert(Contacts.TABLE_NAME, values);
                }
            }

            if (groups != null) {
                for (final String groupId : groups) {
                    values.clear();
                    values.put(Contacts.COL_GROUP_ID, groupId);
                    db.insert(Contacts.TABLE_NAME, values);
                }
            }

            return null;
        });
    }

    @CheckResult
    public Observable<Void> addGroupMembers(final Group group, final Iterable<Person> persons) {
        return updateInTransaction(() -> {
            doAddGroupMemberss(group.getId(), Iterables.transform(persons, Person::getId));
            return null;
        });
    }

    @CheckResult
    public Observable<Void> updatePersons(final Iterable<Person> persons) {
        return updateInTransaction(() -> {
            db.delete(Person.TABLE_NAME, "");


            final ContentValues values = new ContentValues();
            for (Person person : persons) {
                values.clear();
                person.toValues(values);
                db.insert(Person.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            return null;
        });
    }

    private void doAddGroupMemberss(final String groupId, final Iterable<String> members) {
        final ContentValues values = new ContentValues(2);

        for (String memberId : members) {
            values.put(GroupMembers.COL_GROUP_ID, groupId);
            values.put(GroupMembers.COL_PERSON_ID, memberId);
            db.insert(GroupMembers.TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void doUpdateGroupMembers(String groupId, Iterable<String> members) {
        doAddGroupMemberss(groupId, members);

        db.delete(GroupMembers.TABLE_NAME,
                GroupMembers.COL_GROUP_ID + " = ? AND " +
                        GroupMembers.COL_PERSON_ID + " NOT IN " + SqlUtil.toSqlSet(members), groupId);
    }

    public static class AggregateInfo<T1, T2> {
        public final T1 group;
        public final List<T2> members;
        public final int memberCount;

        public AggregateInfo(final T1 group, final List<T2> members, final int memberCount) {
            this.group = group;
            this.members = members;
            this.memberCount = memberCount;
        }
    }
}
