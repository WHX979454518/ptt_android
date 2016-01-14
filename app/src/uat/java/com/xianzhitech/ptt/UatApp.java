package com.xianzhitech.ptt;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

/**
 * Created by fanchao on 13/01/16.
 */
public class UatApp extends App {


    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                final View decorView = activity.getWindow().getDecorView();
                if (decorView instanceof ViewGroup) {
                    final TextView versionNumberView = new TextView(activity);
                    versionNumberView.setText("Build (" + BuildConfig.VERSION_NAME + ")");
                    ((ViewGroup) decorView).addView(versionNumberView,
                            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT));
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                UpdateManager.register(activity, BuildConfig.HOCKEYAPP_ID);
                CrashManager.register(activity, BuildConfig.HOCKEYAPP_ID);
            }

            @Override
            public void onActivityResumed(Activity activity) {

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {
                UpdateManager.unregister();
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }
}
