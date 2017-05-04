package com.zrt.ptt.app.console.mvp.model.Imodel;

import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.List;
import java.util.Set;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public interface IOrgFragmentModel {
    void getDepartUser(callDisposListener listener,String labe);
    interface callDisposListener{
        void callDisposable(Disposable disposable);
        void getNodeData(List<OrgNodeBean> list, int contactUserSize, int onLineUserSize);
        void callOnlineUser(List<OrgNodeBean> list);
    }

    interface  callOnlineData{
        void callDisposable(Disposable disposable);
        void callOnlineUser(List<OrgNodeBean> list);
    }
}
