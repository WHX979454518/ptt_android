package com.zrt.ptt.app.console.mvp.model.ModelImp;

import android.support.v4.util.Pair;
import android.util.Log;

import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.data.ContactDepartment;
import com.xianzhitech.ptt.data.ContactEnterprise;
import com.xianzhitech.ptt.data.ContactGroup;
import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.mvp.bean.OrganizationBean;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOrgFragmentModel;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-2.
 */

public class OrgFragmentModel implements IOrgFragmentModel {
    private List<OrgNodeBean> datas ;
    private static final String all = "ALL";
    private static final String ON_LINE = "ON_LINE";
    private static final String OFF_LINE = "OFF_LINE";
    @Override
    public void getDepartUser(callDisposListener listener,String label) {
        handeAllUser(listener,label);

    }
    private void handeAllUser(callDisposListener listener,String label){
        /*Observable.combineLatest(((AppComponent) App.getInstance().getApplicationContext()).getSignalBroker().getEnterprise(),
                ((AppComponent) App.getInstance().getApplicationContext()).getSignalBroker().getOnlineUserIds(),
                ((AppComponent) App.getInstance().getApplicationContext()).getStorage().getAllGroups(),
                new Function3<ContactEnterprise, Set<String>, List<ContactGroup>, Object>() {
                    @Override
                    public Object apply(@NonNull ContactEnterprise contactEnterprise, @NonNull Set<String> strings, @NonNull List<ContactGroup> contactGroups) throws Exception {
                        return new OrganizationBean(contactEnterprise,strings,contactGroups);
                    }
                }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<OrganizationBean>() {
            @Override
            public void onSubscribe(Disposable disposable) {

            }

            @Override
            public void onNext(OrganizationBean organizationBean) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });*/
        Observable.combineLatest(((AppComponent) App.getInstance().getApplicationContext()).getSignalBroker().getEnterprise(),
                ((AppComponent) App.getInstance().getApplicationContext()).getSignalBroker().getOnlineUserIds(),
                Pair::create).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Pair<ContactEnterprise, Set<String>>>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                if (disposable != null) {
                    listener.callDisposable(disposable);
                }
            }

            @Override
            public void onNext(Pair<ContactEnterprise, Set<String>> data) {
                handUserMassage(listener,data,label);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    private List<OrgNodeBean> returnOrgData(List<OrgNodeBean> departlist ,List<OrgNodeBean> userList){
        for (OrgNodeBean depart:departlist) {
            userList.add(depart);
        }
        return userList;
    }

    //递归部门
    private void addAllDepartment(List<OrgNodeBean> departlist,LinkedHashSet<ContactDepartment> contactDepartment ){
        OrgNodeBean  org;
        for (ContactDepartment department :contactDepartment){
            org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
            departlist.add(org);
            if(department.getChildren().size()!=0){
                addAllDepartment(departlist, (LinkedHashSet<ContactDepartment>) department.getChildren());
            }
        }
    }

    //递归部门中的用户
    private void addAllDepartUser(List<OrgNodeBean> contactUserlist,LinkedHashSet<ContactDepartment> contactDepartment ){
        OrgNodeBean  org;
        for (ContactDepartment department :contactDepartment){
            LinkedHashSet<ContactUser> userSet = (LinkedHashSet<ContactUser>) department.getMembers();
            if(userSet.size()!=0){
                for (ContactUser user:userSet) {
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    contactUserlist.add(org);
                }
            }
            if(department.getChildren().size()!=0){
                addAllDepartUser(contactUserlist, (LinkedHashSet<ContactDepartment>) department.getChildren());
            }
        }
    }
    private void handUserMassage(callDisposListener listener,Pair<ContactEnterprise, Set<String>> data,String label){
        List<OrgNodeBean> online = new ArrayList<OrgNodeBean>();
        ContactEnterprise contactEnterprise = data.first;
        /*if(datas != null){
            datas.clear();
        }*/
        OrgNodeBean org ;
        List<OrgNodeBean> departlist = new ArrayList<OrgNodeBean>();
        List<OrgNodeBean> contactUserlist = new ArrayList<>();
        String compayName = contactEnterprise.getName();
        LinkedHashSet<ContactDepartment> contactDepartment = (LinkedHashSet<ContactDepartment>) contactEnterprise.getDepartments();
        departlist.add(new OrgNodeBean(contactDepartment.iterator().next().getParentObjectId(),null,compayName));
        addAllDepartment(departlist,contactDepartment);
        addAllDepartUser(contactUserlist,contactDepartment);
        LinkedHashSet<ContactUser> contactUser = (LinkedHashSet<ContactUser>) contactEnterprise.getDirectUsers();
        Set<String> setLine = data.second;
       /* setLine.add("500001");
        setLine.add("500003");
        setLine.add("500002");
        setLine.add("500004");
        setLine.add("500005");*/
        int userNum =0;
        switch (label){
            case all:
//                List<OrgNodeBean> allOrgList = new ArrayList<OrgNodeBean>();
                for (ContactUser user :contactUser){
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    for (String id:setLine) {
                        //TODO 待有实际部门值时移除此段随机代码
                        Random rd = new Random(); //创建一个Random类对象实例
                        int x = rd.nextInt(3)+1;
                        if(org.get_id().equals(id)){
                            org.setOnline(true);
                            online.add(org);
                            break;
                        }
                    }
                    contactUserlist.add(org);
                }
                userNum = contactUserlist.size();
                listener.getNodeData(returnOrgData(departlist,contactUserlist),userNum,setLine.size());
                break;
            case ON_LINE:
//                List<OrgNodeBean> onLineOrgList = new ArrayList<OrgNodeBean>();
                for (ContactUser user :contactUser){
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    for (String id:setLine) {
                        if(org.get_id().equals(id)){
                            org.setOnline(true);
                            Random rd = new Random(); //创建一个Random类对象实例
                            int x = rd.nextInt(3)+1;
                            online.add(org);
                            contactUserlist.add(org);
                            break;
                        }
                    }
                }
                userNum = contactUserlist.size();
                listener.getNodeData(returnOrgData(departlist,contactUserlist),userNum,setLine.size());
                break;
            case OFF_LINE:
//                List<OrgNodeBean> offLineOrgList = new ArrayList<OrgNodeBean>();
                List<ContactUser> contaUser = new ArrayList<ContactUser>();
                for(ContactUser user :contactUser){
                    contaUser.add(user);
                }
                Iterator<ContactUser> it = contaUser.iterator();
                //TODO 操作集合移除元素时，不能用foreach循环遍历会出错，操作同一资源
                while (it.hasNext()){
                    ContactUser user = it.next();
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    for (String id:setLine) {
                        if(org.get_id().equals(id)){
                            it.remove();
                            org.setOnline(true);
                            online.add(org);
                            break;
                        }
                    }
                }
                for (ContactUser user :contaUser){
                    org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                    Random rd = new Random(); //创建一个Random类对象实例
                    int x = rd.nextInt(3)+1;
                    org.setOnline(false);
                    contactUserlist.add(org);
                }
                userNum = contactUserlist.size();
                listener.getNodeData(returnOrgData(departlist,contactUserlist),userNum,setLine.size());
                break;
        }

        listener.callOnlineUser(online);
        departlist = null;
        contactUserlist = null;
        userNum=0;

    }

}
