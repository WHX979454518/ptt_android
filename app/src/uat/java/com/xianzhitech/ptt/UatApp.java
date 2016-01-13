package com.xianzhitech.ptt;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.xianzhitech.ptt.ui.MainActivity;
import com.xianzhitech.ptt.ui.room.RoomActivity;

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
                if (decorView instanceof FrameLayout) {
                    final TextView versionNumberView = new TextView(activity);
                    versionNumberView.setText("Build (" + BuildConfig.VERSION_NAME + ")");
                    ((FrameLayout) decorView).addView(versionNumberView,
                            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT));
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

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
