package com.zrt.ptt.app.console.mvp.view.IView;

import com.baidu.mapapi.model.LatLng;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;

import org.json.JSONObject;

import java.util.List;

/**
 * Created by surpass on 2017-4-27.
 */

public interface IMainActivityView {
    void UpDateOrganization(JSONObject json);

    /**
     * 点击定位传递数据接口方法
     * @param locations
     */
    void showLocation(List<LatLng> locations);

}
