package com.zrt.ptt.app.console.mvp.presenter;

import android.os.Handler;
import android.os.Message;

import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.mvp.model.ModelImp.OrganizationMain;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;

import org.json.JSONObject;

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

    public  void UpDataOrganzation(){
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

    class orgHandler  extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

}
