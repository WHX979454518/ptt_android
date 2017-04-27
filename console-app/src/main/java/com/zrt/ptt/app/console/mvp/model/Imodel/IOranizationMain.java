package com.zrt.ptt.app.console.mvp.model.Imodel;

import org.json.JSONObject;

/**
 * Created by surpass on 2017-4-27.
 * 获取组织架构新信息
 */

public interface IOranizationMain {
     void getOrganizationData(CallBackListener listener);
    interface CallBackListener{
        void upDateData(JSONObject json);
    }
}
