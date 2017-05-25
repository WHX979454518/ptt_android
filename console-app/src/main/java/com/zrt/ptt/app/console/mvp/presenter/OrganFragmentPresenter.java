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
import java.util.Set;

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

    public void showAll(String label){
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
            public void getNodeData(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize,String compayName) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        iFragmentView.showAll(list,contactUserSize,onLineUserSize,compayName);
                    }
                });

            }

            @Override
            public void callOnlineUser(List<OrgNodeBean> list) {

            }
        },label);
    }

    //处理在线用户展示
    public void showOnlineUser(String label){
        iFragmentModel.getDepartUser(new IOrgFragmentModel.callDisposListener() {
            @Override
            public void callDisposable(Disposable disposable) {
                iFragmentView.callDisposable(disposable);
            }

            @Override
            public void getNodeData(List<OrgNodeBean> list, int contactUserSize, int onLineUserSize,String compayName) {
                iFragmentView.showOnLine(list,contactUserSize,onLineUserSize);
            }

            @Override
            public void callOnlineUser(List<OrgNodeBean> list) {

            }
        },label);
    }

    //处理在线用户展示
    public void showOfflineUser(String label){
        iFragmentModel.getDepartUser(new IOrgFragmentModel.callDisposListener() {
            @Override
            public void callDisposable(Disposable disposable) {
                iFragmentView.callDisposable(disposable);
            }

            @Override
            public void getNodeData(List<OrgNodeBean> list, int contactUserSize, int onLineUserSize,String compayName) {
                iFragmentView.showOffline(list,contactUserSize,onLineUserSize);
            }

            @Override
            public void callOnlineUser(List<OrgNodeBean> list) {

            }
        },label);
    }
    class orgHandler  extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

}
