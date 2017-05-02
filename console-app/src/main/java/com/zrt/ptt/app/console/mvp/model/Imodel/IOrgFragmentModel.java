package com.zrt.ptt.app.console.mvp.model.Imodel;

import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public interface IOrgFragmentModel {
    void getDepartUser(callDisposListener listener);
    interface callDisposListener{
        void callDisposable(Disposable disposable);
        void getNodeData(List<OrgNodeBean> list,List<ContactUser> contactUser);
    }
}
