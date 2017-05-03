package com.zrt.ptt.app.console.mvp.view.IView;

import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public interface IOrgFragmentView {
    //第二个参数方便统计用户数量
    void showAll(List<OrgNodeBean> list,List<ContactUser> contactUser);
    void showOnLine(List<OrgNodeBean> list);
    void showOffline();
    void showSlected();
    void callDisposable(Disposable disposable);
}
