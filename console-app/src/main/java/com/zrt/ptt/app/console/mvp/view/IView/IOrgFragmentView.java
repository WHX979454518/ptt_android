package com.zrt.ptt.app.console.mvp.view.IView;

import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.List;
import java.util.Set;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public interface IOrgFragmentView {
    //第二个参数方便统计用户数量
    void showAll(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize,String compayName);
    void showOnLine(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize);
    void showOffline(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize);
    void showSlected();
    void callDisposable(Disposable disposable);

}
