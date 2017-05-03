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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public class OrgFragmentModel implements IOrgFragmentModel {
    private List<OrgNodeBean> datas ;
    @Override
    public void getDepartUser(callDisposListener listener) {
       List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
        handeAllUser(mDatas,listener);

    }
    private List<OrgNodeBean> getAllMsagge(){
        List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
        return mDatas;
    }

    private void getAllUser(Object listener){
        datas = new ArrayList<OrgNodeBean>();
        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();
        contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ContactEnterprise>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                if(disposable!=null){
                    if(listener.getClass().equals(callDisposListener.class)){
                        ((callDisposListener)listener).callDisposable(disposable);
                    }else {
                        ((callOnlineData)listener).callDisposable(disposable);
                    }
                }
            }

            @Override
            public void onNext(ContactEnterprise contactEnterprise) {
                OrgNodeBean org ;
                List<ContactDepartment> contactDepartment = contactEnterprise.getDepartments();
                for (ContactDepartment department :contactDepartment){
                    org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
                    Log.e(""+department.getName()+":",""+department.getName());
                    datas.add(org);
                }
                List<ContactUser> contactUser = contactEnterprise.getDirectUsers();
                for (ContactUser user:contactUser){
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    datas.add(org);
                }
            }

            @Override
            public void onError(Throwable throwable) {

                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {

            }
        });

    }

    private void handeAllUser(List<OrgNodeBean> mDatas ,callDisposListener listener){
        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();
        contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ContactEnterprise>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                if(disposable!=null){
                    listener.callDisposable(disposable);
                }
            }

            @Override
            public void onNext(ContactEnterprise contactEnterprise) {
                OrgNodeBean org ;
                List<ContactDepartment> contactDepartment = contactEnterprise.getDepartments();
                for (ContactDepartment department :contactDepartment){//这里是添加完部门信息?ok
                    org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
                    Log.e(""+department.getName()+":",""+department.getName());
                    mDatas.add(org);
                }
                List<ContactUser> contactUser = contactEnterprise.getDirectUsers();
                for (ContactUser user:contactUser){//这是添加完用户信息ok只不过部门少很多字段
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    mDatas.add(org);
                }
                listener.getNodeData(mDatas,contactUser);
            }

            @Override
            public void onError(Throwable throwable) {

                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    public void getOnLineUser(callOnlineData onlinelistener) {
         getAllUser(onlinelistener);
        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<Set<String>> onlineUserIds = appComponent.getSignalBroker().getOnlineUserIds();
        onlineUserIds.observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Set<String>>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                if(disposable !=null ){
                    onlinelistener.callDisposable(disposable);
                }
            }

            @Override
            public void onNext(Set<String> data) {
                //// TODO: 2017-5-3 据说在线人员只有管理员账户才会有,先用死数据模拟
                List<OrgNodeBean> online = new ArrayList<OrgNodeBean>();
                data.add("500001");
                data.add("500003");
                data.add("500002");
                data.add("500004");
                data.add("500005");
                Iterator<String> it = data.iterator();
                while(it.hasNext()){
                    String id = it.next();
                     Log.e("data_id","data_id_myself:----------------------->"+id);
                    while (datas.iterator().hasNext()){
                        OrgNodeBean orgNodeBean = datas.iterator().next();
                        if(id.equals(orgNodeBean.get_id())){
                            online.add(orgNodeBean);

                        }

                    }
                    Log.e("data_id","orgNodeBean.get_id():----------------------->"+datas);
                }
                onlinelistener.callOnlineUser(online);
                Log.e("data_id","online.size():----------------------->"+online.size());
                it = null;


            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });
    }


}
