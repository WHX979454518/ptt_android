package com.zrt.ptt.app.console.mvp.presenter;

import android.os.Handler;
import android.os.Message;

import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.broker.RoomMode;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.mvp.model.ModelImp.OrganizationMain;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by surpass on 2017-4-27.
 */

public class MainActivityPresenter {
    private IOranizationMain iOrgMain;
    private IMainActivityView iMainView;


    private orgHandler handler = new orgHandler();

    public MainActivityPresenter(IMainActivityView iMainView) {
        this.iOrgMain = new OrganizationMain();
        this.iMainView = iMainView;
    }

    public void UpDataOrganzation() {
        iOrgMain.getOrganizationData(new IOranizationMain.CallBackListener() {
            @Override
            public void upDateData(JSONObject json) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        iMainView.UpDateOrganization(json);
                    }
                });
            }
        });

    }

    //单人定位
    public void showLocation(List<String> locationUserIds) {
        iOrgMain.getSingleLocation(new IOranizationMain.callBackLocations() {
            @Override
            public void getSingleData(List<LastLocationByUser> lastLocationByUsers) {
                List<LatLng> locations = new ArrayList<LatLng>();
                if (lastLocationByUsers.size() != 0) {
                    LastLocationByUser cationUser = lastLocationByUsers.get(0);
                    if (cationUser != null) {
                        LatLng lng = new LatLng(cationUser.getLatLng().getLat(), cationUser.getLatLng().getLng());
                        locations.add(lng);
                        iMainView.showLocation(locations);
                    }
                } else return;


            }

            @Override
            public void getAllLocations(List<LastLocationByUser> lastLocationByUsers) {

            }
        }, locationUserIds);

    }


    //多人定位
    public void showLocations(List<String> locationUserIds) {
        iOrgMain.getAllLocation(new IOranizationMain.callBackLocations() {
            @Override
            public void getSingleData(List<LastLocationByUser> lastLocationByUsers) {

            }

            @Override
            public void getAllLocations(List<LastLocationByUser> lastLocationByUsers) {
                List<LatLng> locations = new ArrayList<LatLng>();
                if (lastLocationByUsers.size() != 0) {
                    for (LastLocationByUser cationsUser : lastLocationByUsers) {
                        if (cationsUser != null) {
                            LatLng lng = new LatLng(cationsUser.getLatLng().getLat(), cationsUser.getLatLng().getLng());
                            locations.add(lng);
                            iMainView.showLocation(locations);
                        }
                    }
                }
            }
        }, locationUserIds);
    }

    public void showChatkRoom(List<String> userIds, List<String> groupIds, RoomMode roomMode) {
        iMainView.showChatRoomView(userIds, groupIds, roomMode);
    }

    public void showHistoryTraceDialog() {
        iMainView.showHistorytraceDialog();
    }

    class orgHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

}
