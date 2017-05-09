package com.zrt.ptt.app.console.mvp.model.ModelImp;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.baidu.mapapi.model.LatLng;
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
    public void getSingleLocation(callBackLocations backLocation) {
//        Observable.combineLatest().observeOn(AndroidSchedulers.mainThread()).subscribeOn(new Observable<>());
        backLocation.getSingleData();
    }

    @Override
    public void getAllLocation(callBackLocations backLocation) {
//        Observable.combineLatest().observeOn(AndroidSchedulers.mainThread()).subscribeOn(new Observable<>());
        backLocation.getAllLocations();
    }

}
