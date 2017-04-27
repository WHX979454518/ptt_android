package com.zrt.ptt.app.console.mvp.model.ModelImp;

import android.os.Handler;
import android.os.Message;

import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.net.OkHttpKit;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

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


}
