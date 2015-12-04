package com.podkitsoftware.shoumi;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.SimpleArrayMap;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.Group$Table;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.GroupMember$Table;
import com.podkitsoftware.shoumi.model.Person;
import com.podkitsoftware.shoumi.model.Person$Table;
import com.raizlabs.android.dbflow.runtime.TransactionManager;
import com.raizlabs.android.dbflow.runtime.transaction.BaseTransaction;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.ColumnAlias;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Insert;
import com.raizlabs.android.dbflow.sql.language.Join;
import com.raizlabs.android.dbflow.sql.language.Select;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public enum Broker {
    INSTANCE;

    final Scheduler queryScheduler = Schedulers.computation();
    final Scheduler modifyScheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    final Handler mainHandler = new Handler(Looper.myLooper());

    private Observable<Void> observeContent(final Uri...uris) {
        return Observable.create(subscriber -> {
            final ContentResolver resolver = App.getInstance().getContentResolver();
            final ContentObserver observer = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    subscriber.onNext(null);
                }
            };

            for (final Uri uri : uris) {
                resolver.registerContentObserver(uri, true, observer);
            }

            subscriber.add(Subscriptions.create(() -> resolver.unregisterContentObserver(observer)));
        });
    }

    private <T> Observable<T> queryActive(final Func0<T> query, final Uri...uris) {
        final Observable<T> deferred = Observable.defer(() -> Observable.just(query.call())).observeOn(queryScheduler);

        return Observable.merge(
                observeContent(uris).debounce(200, TimeUnit.MILLISECONDS).flatMap(aVoid -> deferred),
                deferred);
    }

    private Observable<Void> modifyActive(final Runnable query) {
        return Observable.<Void>defer(() -> {
            query.run();
            return Observable.just(null);
        }).subscribeOn(modifyScheduler);
    }

    public Observable<List<Group>> getGroups() {
        return queryActive(() ->
                        new Select().from(Group.class).queryList(),
                Group.BASE_URI);
    }

    public Observable<List<Person>> getGroupMembers(final Group group) {
        return queryActive(() ->
                        new Select().from(Person.class).as("P")
                                .join(GroupMember.class, Join.JoinType.LEFT).as("GM")
                                .on(Condition.column(ColumnAlias.columnWithTable("GM", GroupMember$Table.PERSON_PERSON_ID))
                                        .eq(Condition.column(ColumnAlias.columnWithTable("P", Person$Table.ID))))
                                .where(Condition.column(ColumnAlias.columnWithTable("GM", GroupMember$Table.GROUP_GROUP_ID)).eq(group.getId()))
                                .queryList(),
                group.getUri(),
                GroupMember.BASE_URI);
    }

    public Observable<Void> updateGroups(final Collection<Group> groups, final SimpleArrayMap<Long, Long[]> groupMembers) {
        return modifyActive(() -> TransactionManager.getInstance().addTransaction(new BaseTransaction<Void>() {
            @Override
            public Void onExecute() {
                Insert.into(Group.class).orReplace().values(groups).queryClose();
                new Delete().from(Group.class).where(Condition.column(Group$Table.ID).notIn(groups)).queryClose();

                for (int i = 0, size = groupMembers.size(); i < size; i++) {
                    final long groupId = groupMembers.keyAt(i);
                    final Long[] members = groupMembers.valueAt(i);

                    doUpdateGroup(groupId, members);
                }

                App.getInstance().getContentResolver().notifyChange(Group.BASE_URI, null);
                App.getInstance().getContentResolver().notifyChange(GroupMember.BASE_URI, null);

                return null;
            }
        }));
    }

    public Observable<Void> updateGroupMembers(final Group group, final Long[] groupMembers) {
        return modifyActive(() -> TransactionManager.getInstance().addTransaction(new BaseTransaction<Void>() {
            @Override
            public Void onExecute() {
                doUpdateGroup(group.getId(), groupMembers);
                return null;
            }
        }));
    }

    private static void doUpdateGroup(long groupId, Long[] members) {
        new Delete().from(GroupMember.class).where(Condition.column(GroupMember$Table.GROUP_GROUP_ID).eq(groupId)).queryClose();
        Insert.into(GroupMember.class).columns(GroupMember$Table.PERSON_PERSON_ID).values(members);
    }

}
