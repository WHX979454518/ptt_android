package com.podkitsoftware.shoumi;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.util.SimpleArrayMap;

import com.android.debug.hv.ViewServer;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp.StethoInterceptor;
import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.Person;
import com.squareup.okhttp.OkHttpClient;

import java.util.Arrays;
import java.util.List;

/**
 * Created by fanchao on 7/12/15.
 */
public class DevApp extends App {

    @Override
    public void onCreate() {
        super.onCreate();

        Stetho.initializeWithDefaults(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
                ViewServer.get(activity).addWindow(activity);
            }

            @Override
            public void onActivityStarted(final Activity activity) {
            }

            @Override
            public void onActivityResumed(final Activity activity) {
                ViewServer.get(activity).setFocusedWindow(activity);
            }

            @Override
            public void onActivityPaused(final Activity activity) {
            }

            @Override
            public void onActivityStopped(final Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(final Activity activity, final Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(final Activity activity) {
                ViewServer.get(activity).removeWindow(activity);
            }
        });

        final List<Person> persons = Arrays.asList(
                new Person("1", "张三"),
                new Person("2", "李四"),
                new Person("3", "王小五"),
                new Person("4", "李小六"),
                new Person("5", "不认识"));

        final List<Group> groups = Arrays.asList(
                new Group("1", "火星自驾群"),
                new Group("2", "北京自驾群"),
                new Group("3", "非主流群"));

        final SimpleArrayMap<Group, List<String>> groupMembers = new SimpleArrayMap<>();
        groupMembers.put(groups.get(0), Arrays.asList(persons.get(0).getId(), persons.get(1).getId()));
        groupMembers.put(groups.get(1), Arrays.asList(persons.get(2).getId(), persons.get(4).getId()));
        groupMembers.put(groups.get(2), Arrays.asList(persons.get(0).getId(), persons.get(1).getId(), persons.get(2).getId(), persons.get(3).getId()));

        providesBroker().updatePersons(persons).subscribe();
        providesBroker().updateGroups(groups, groupMembers).subscribe();
        providesBroker().updateOnlineUsers(Arrays.asList(persons.get(0).getId(), persons.get(3).getId()));
    }

    @Override
    public OkHttpClient providesHttpClient() {
        final OkHttpClient okHttpClient = super.providesHttpClient();
        okHttpClient.networkInterceptors().add(new StethoInterceptor());
        return okHttpClient;
    }
}
