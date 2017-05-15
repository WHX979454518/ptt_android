package com.zrt.ptt.app.console.mvp.model.ModelImp;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.xianzhitech.ptt.broker.SignalBroker;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.activity.MainActivity;
import com.zrt.ptt.app.console.mvp.view.fragment.ConsoleMapFragment;
import com.zrt.ptt.app.console.net.OkHttpKit;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;

/**
 * Created by surpass on 2017-4-27.
 */

public class OrganizationMain implements IOranizationMain {


    @Override
    public void getOrganizationData(CallBackListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    listener.upDateData(OkHttpKit.connGet("https://netptt.cn:20007/api/contact/organization", null));
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    //rxjava异步处理获得数据
    @Override
    public void getSingleLocation(callBackLocations backLocation,List<String> locationUserIds) {
        SignalBroker signalBroker =((AppComponent)App.getInstance().getApplicationContext()).getSignalBroker();
        signalBroker .getLastLocationByUserIds(locationUserIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<LastLocationByUser>>() {
                    @Override
                    public void accept(@NonNull List<LastLocationByUser> lastLocationByUsers) throws Exception {
                        backLocation.getSingleData(lastLocationByUsers);

                    }
                });
    }

    @Override
    public void getAllLocation(callBackLocations backLocation,List<String> locationUserIds) {
        ((AppComponent)App.getInstance().getApplicationContext()).getSignalBroker().getLastLocationByUserIds(locationUserIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<LastLocationByUser>>() {
                    @Override
                    public void accept(@NonNull List<LastLocationByUser> lastLocationByUsers) throws Exception {
                        backLocation.getAllLocations(lastLocationByUsers);
                    }
                });
    }

    //历史轨迹
    @Override
    public void getHistoryLocation(List<String> locationUserIds,Long startTime, long endTime) {
        ((AppComponent)App.getInstance().getApplicationContext()).getSignalBroker().findUserLocations(locationUserIds,startTime,endTime)
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<List<UserLocation>>() {
            @Override
            public void accept(@NonNull List<UserLocation> userLocations) throws Exception {

            }
        });
    }

}
