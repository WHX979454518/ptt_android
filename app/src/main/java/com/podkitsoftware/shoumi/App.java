package com.podkitsoftware.shoumi;

import android.app.Application;

import com.raizlabs.android.dbflow.config.FlowManager;

/**
 * Created by fanchao on 4/12/15.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FlowManager.init(this);
    }
}
