package com.podkitsoftware.shoumi;

import android.app.Activity;
import android.os.Bundle;

import com.android.debug.hv.ViewServer;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp.StethoInterceptor;
import com.squareup.okhttp.OkHttpClient;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.socket.client.Manager;

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

        final String name = Manager.class.getName();
        Logger.getLogger(name).setLevel(Level.ALL);

        providesAuthService().login("500002", "000000").subscribe();
    }

    @Override
    public OkHttpClient providesHttpClient() {
        final OkHttpClient okHttpClient = super.providesHttpClient();
        okHttpClient.networkInterceptors().add(new StethoInterceptor());
        return okHttpClient;
    }
}
