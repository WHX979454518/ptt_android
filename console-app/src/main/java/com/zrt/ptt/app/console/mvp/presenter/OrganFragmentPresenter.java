package com.zrt.ptt.app.console.mvp.presenter;

import android.os.Handler;
import android.os.Message;

import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOrgFragmentModel;
import com.zrt.ptt.app.console.mvp.model.ModelImp.OrgFragmentModel;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.view.IView.IOrgFragmentView;

import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public class OrganFragmentPresenter {
    private IOrgFragmentModel iFragmentModel;
    private IOrgFragmentView iFragmentView;
    private orgHandler handler = new orgHandler();

    public OrganFragmentPresenter(IOrgFragmentView iFragmentView) {
        this.iFragmentModel = new OrgFragmentModel();
        this.iFragmentView = iFragmentView;
    }

    public void showAll(){
        iFragmentModel.getDepartUser(new IOrgFragmentModel.callDisposListener() {
            @Override
            public void callDisposable(Disposable disposable) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        iFragmentView.callDisposable(disposable);
                    }
                });
            }

            @Override
            public void getNodeData(List<OrgNodeBean> list,List<ContactUser> contactUser) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        iFragmentView.showAll(list,contactUser);
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
