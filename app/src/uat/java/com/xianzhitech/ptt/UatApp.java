package com.xianzhitech.ptt;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.FeedbackManager;
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
            public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
                final View decorView = activity.getWindow().getDecorView();
                if (decorView instanceof ViewGroup) {
                    decorView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final LinearLayout debugInfoLayout = new LinearLayout(activity);
                            debugInfoLayout.setOrientation(LinearLayout.HORIZONTAL);

                            final TextView versionNumberView = new TextView(activity);
                            versionNumberView.setText("Build (" + BuildConfig.VERSION_NAME + ")");
                            versionNumberView.setTextColor(Color.BLACK);
                            debugInfoLayout.addView(versionNumberView);

//                            final Button feedbackButton = new Button(activity);
//                            feedbackButton.setText("反馈问题");
//                            feedbackButton.setTextColor(Color.BLACK);
//                            debugInfoLayout.addView(feedbackButton);
//                            feedbackButton.setOnClickListener(new View.OnClickListener() {
//                                @Override
//                                public void onClick(final View v) {
//                                    FeedbackManager.takeScreenshot(activity);
//                                    activity.startActivity(new Intent(activity, FeedbackActivity.class));
//                                }
//                            });

                            ((ViewGroup) decorView).addView(debugInfoLayout,
                                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT));
                        }
                    }, 1000);
                }

                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                UpdateManager.register(activity, BuildConfig.HOCKEYAPP_ID);
                CrashManager.register(activity, BuildConfig.HOCKEYAPP_ID);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof FeedbackActivity) && !(activity instanceof net.hockeyapp.android.FeedbackActivity)) {
                    FeedbackManager.setActivityForScreenshot(activity);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (!(activity instanceof FeedbackActivity) && !(activity instanceof net.hockeyapp.android.FeedbackActivity)) {
                    FeedbackManager.unsetCurrentActivityForScreenshot(activity);
                }
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
