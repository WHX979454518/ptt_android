package com.zrt.ptt.app.console.mvp.model.ModelImp;

import android.util.Log;

import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.data.ContactDepartment;
import com.xianzhitech.ptt.data.ContactEnterprise;
import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOrgFragmentModel;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public class OrgFragmentModel implements IOrgFragmentModel {
    @Override
    public void getDepartUser(callDisposListener listener) {
       List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();
        contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ContactEnterprise>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                Log.e("Disposable","Disposable已经执行了");
                if(disposable!=null){
                    listener.callDisposable(disposable);
                }
            }

            @Override
            public void onNext(ContactEnterprise contactEnterprise) {
                OrgNodeBean org ;
                List<ContactDepartment> contactDepartment = contactEnterprise.getDepartments();
                Log.e("contactDepartment","contactDepartment:"+contactDepartment);
                for (ContactDepartment department :contactDepartment){
                    org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
                    Log.e(""+department.getName()+":",""+department.getName());
                    mDatas.add(org);
                }
                List<ContactUser> contactUser = contactEnterprise.getDirectUsers();
                Log.e("contactUser:------->",""+contactUser);
                for (ContactUser user:contactUser){
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    mDatas.add(org);
                }
                listener.getNodeData(mDatas,contactUser);
            }

            @Override
            public void onError(Throwable throwable) {

                Log.e("throwable",""+throwable.getMessage());
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {

            }
        });

    }


}
