package com.zrt.ptt.app.console.mvp.view.fragment;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.data.ContactDepartment;
import com.xianzhitech.ptt.data.ContactEnterprise;
import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.presenter.OrganFragmentPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IOrgFragmentView;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TreeListViewAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * A simple {@link Fragment} subclass.
 */


public class OrganizationFragment extends Fragment implements View.OnClickListener,IOrgFragmentView{

    private ListView treeLv;
    private MyTreeListViewAdapter<OrgNodeBean> adapter;
    private List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;
    private Disposable dposable;
    private LinearLayout showAllUsers,showOnlineUsers,showOfflineUsers,showSelectedUsers;
    private OrganFragmentPresenter fragPresen;
    private TextView allUserNum;

    public OrganizationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        super.onCreate(savedInstanceState);
         view = inflater.inflate(R.layout.fragment_organization, container, false);
        treeLv = (ListView) view.findViewById(R.id.tree_lv);
        allUserNum = (TextView) view.findViewById(R.id.all_user_nun);
        showAllUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_all);
        showAllUsers.setOnClickListener(this);
        showOnlineUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_online);
        showOnlineUsers.setOnClickListener(this);
        showOfflineUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_offline);
        showOfflineUsers.setOnClickListener(this);
        showSelectedUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_selected);
        showSelectedUsers.setOnClickListener(this);
        fragPresen = new OrganFragmentPresenter(this);

        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();
        contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ContactEnterprise>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                //it can stop this subscription,you should do this  when activity distory ok
              /*  if(!disposable.isDisposed()){
                    disposable.dispose();
                }*/
                Log.v("tag","onSubscrbe------------:");
               /* if(disposable != null){
                    dposable = disposable;
                }*/

            }

            @Override
            public void onNext(ContactEnterprise contactEnterprise) {
                List<ContactDepartment> contactDepartment = contactEnterprise.getDepartments();
                OrgNodeBean org ;
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

                try {
                    adapter = new MyTreeListViewAdapter<OrgNodeBean>(treeLv, getActivity(),
                            mDatas, 0, isHide);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                adapter.setOnTreeNodeClickListener(new TreeListViewAdapter.OnTreeNodeClickListener() {
                    @Override
                    public void onClick(Node node, int position) {
                        if (node.isLeaf()) {
                            Toast.makeText(getActivity().getApplicationContext(), node.getName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCheckChange(Node node, int position,
                                              List<Node> checkedNodes) {
                        // TODO Auto-generated method stub

                        StringBuffer sb = new StringBuffer();
                        for (Node n : checkedNodes) {

                        }
                    }

                });

                treeLv.setAdapter(adapter);

            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("throwable",""+throwable.getMessage());
                throwable.printStackTrace();
                Log.e("出错啦","出错啦");
            }

            @Override
            public void onComplete() {
                Log.e("tag","onComplete----------------->");
            }
        });

        return view;

    }

    public void getOrgAndUserData(){
        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();
        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();
        contactEnterprise.observeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ContactEnterprise>() {
            @Override
            public void onSubscribe(Disposable disposable) {
                Log.e("Disposable","Disposable已经执行了");
//                dposable = disposable;
            }

            @Override
            public void onNext(ContactEnterprise contactEnterprise) {
                List<ContactDepartment> contactDepartment = contactEnterprise.getDepartments();
                OrgNodeBean org ;
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

                try {
                    adapter = new MyTreeListViewAdapter<OrgNodeBean>(treeLv, getActivity(),
                            mDatas, 0, isHide);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                adapter.setOnTreeNodeClickListener(new TreeListViewAdapter.OnTreeNodeClickListener() {
                    @Override
                    public void onClick(Node node, int position) {
                        if (node.isLeaf()) {
                            Toast.makeText(getActivity().getApplicationContext(), node.getName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCheckChange(Node node, int position,
                                              List<Node> checkedNodes) {
                        // TODO Auto-generated method stub

                        StringBuffer sb = new StringBuffer();
                        for (Node n : checkedNodes) {

                        }
                    }

                });

                treeLv.setAdapter(adapter);

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


    //模拟json数据
    public void initDatas() {
        String json = "{\"status\":200,\"data\":[{\"lastLocationTimeCST\":\"\",\"positionname\":\"总经理\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcogiAYgUMAABsrdgKnKM450.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec69ff3ca2d0007beb0e7\",\"commander\":1,\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"phoneNbr\":11122233344,\"idNumber\":9100001,\"name\":\"指挥号\",\"__v\":0,\"position\":\"59014861f3ca2d0007beb151\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"mail\":\"\",\"privileges\":{\"priority\":null,\"viewMap\":false,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec91df3ca2d0007beb0ec\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"五杀\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"asd@dom.com\",\"idNumber\":9100002,\"phoneNbr\":13500000000,\"__v\":0,\"position\":\"59014876f3ca2d0007beb152\",\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901491ff3ca2d0007beb156\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"流浪汉\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"59014876f3ca2d0007beb152\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"aaad@dom.com\",\"idNumber\":9100003,\"phoneNbr\":13566666666,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"平民\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcoqaAXNIPAABq9HszdDo314.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901495af3ca2d0007beb157\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"打手\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"590148a2f3ca2d0007beb154\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"adf@dom.com\",\"idNumber\":9100004,\"phoneNbr\":13522222222,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"_id\":\"58fec8a1f3ca2d0007beb0eb\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"青铜\",\"__v\":0},{\"_id\":\"59014806f3ca2d0007beb150\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"撸撸撸\",\"__v\":0},{\"father\":-1,\"name\":\"czqtest\",\"_id\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true}]}";
        Gson gson = new Gson();
        JSONObject data = null;
        try {
            data = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (data.has("data")) {
            try {
                String datas = data.getString("data");
                List<OrgNodeBean> goodsLists = gson.fromJson(datas, new TypeToken<List<OrgNodeBean>>() {
                }.getType());
                for (OrgNodeBean user : goodsLists) {
                    mDatas.add(user);
                    System.out.printf(user.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dposable != null) {
            dposable.dispose();
        }

    }

    //所有人员，在线，离线，已勾选点击事件
    @Override
    public void onClick(View v) {

        switch (v.getId()){
            case R.id.show_orgzation_all:
                mDatas.clear();
                fragPresen.showAll();

                break;
            case R.id.show_orgzation_online:
                break;
            case R.id.show_orgzation_offline:
                break;
            case R.id.show_orgzation_selected:
                break;
        }
    }

    //通过presenter拿到所有数据回调展示
    @Override
    public void showAll(List<OrgNodeBean> list,List<ContactUser> contactUser) {
        mDatas = list;
        adapter.notifyDataSetChanged();
        allUserNum.setText("("+contactUser.size()+")");
    }

    //通过presenter拿到在线用户数据回调展示
    @Override
    public void showOnLine() {

    }

    //通过presenter拿到离线用户数据回调展示
    @Override
    public void showOffline() {

    }

    //通过presenter拿到已勾选用户数据回调展示
    @Override
    public void showSlected() {

    }

    //通过presenter拿到RXjava Disposable 截断下游接收数据在ondestroy中处理
    @Override
    public void callDisposable(Disposable disposable) {
        if(disposable != null){
            dposable = disposable;
        }

    }
}
