package com.zrt.ptt.app.console.mvp.model.Imodel;

import android.content.Context;

import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;

import org.json.JSONObject;

import java.util.List;

/**
 * Created by surpass on 2017-4-27.
 * 获取组织架构新信息
 */

public interface IOranizationMain {
     void getOrganizationData(CallBackListener listener);
    interface CallBackListener{
        void upDateData(JSONObject json);
    }
    interface callBackLocations{
        void getSingleData(List<LastLocationByUser> lastLocationByUsers);
        void getAllLocations(List<LastLocationByUser> lastLocationByUsers);
    }
    void getSingleLocation(callBackLocations backLocation,List<String> locationUserIds);
    void getAllLocation(callBackLocations backLocation,List<String> locationUserIds);
}
