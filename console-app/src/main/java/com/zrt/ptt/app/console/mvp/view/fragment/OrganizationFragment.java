package com.zrt.ptt.app.console.mvp.view.fragment;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.xianzhitech.ptt.broker.RoomMode;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.presenter.MainActivityPresenter;
import com.zrt.ptt.app.console.mvp.presenter.OrganFragmentPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;
import com.zrt.ptt.app.console.mvp.view.IView.IOrgFragmentView;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TreeListViewAdapter;

import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Subscription;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;


/**
 * A simple {@link Fragment} subclass.
 */


public class OrganizationFragment extends Fragment implements View.OnClickListener,IOrgFragmentView{

    private ListView treeLv;
    private MyTreeListViewAdapter<OrgNodeBean> adapter;
    private List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
    private List<OrgNodeBean> checkDatas = new ArrayList<OrgNodeBean>();//选中专用存储所有用户过来的新数据
    public List<Node> NodeAddDelet = new ArrayList<Node>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;
    private Disposable dposable;
    private LinearLayout showAllUsers,showOnlineUsers,showOfflineUsers,showSelectedUsers;
    private OrganFragmentPresenter fragPresen;
    private MainActivityPresenter mainActivityPresenter;
    private TextView allUserNum,onlinePersNum,offlinePersNum,selectedPersNum;
    private static final String all = "ALL";
    private static final String ON_LINE = "ON_LINE";
    private static final String OFF_LINE = "OFF_LINE";
    private ImageView userLocation,talk,trajectory_btn;
    private List<LatLng> locations = new ArrayList<>();
    private HashSet<String> locationUserIds = new HashSet<>();
    private HashSet<String> multiMediaUserID = new HashSet<>();
    private Timer timer;
    private TimerTask timerTask;

    public OrganizationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mainActivityPresenter = new MainActivityPresenter((IMainActivityView) context);
        }catch (ClassCastException e) {
            throw new ClassCastException(getActivity().getClass().getName()
                    +" must implements interface MyListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        super.onCreate(savedInstanceState);
         view = inflater.inflate(R.layout.fragment_organization, container, false);
        initView(view);
        fragPresen.showAll(all);
        return view;

    }

    private void initView(View view){
        treeLv = (ListView) view.findViewById(R.id.tree_lv);
        allUserNum = (TextView) view.findViewById(R.id.all_user_nun);
        onlinePersNum = (TextView) view.findViewById(R.id.online_pers_num);
        offlinePersNum = (TextView) view.findViewById(R.id.offline_pers_num);
        selectedPersNum = (TextView) view.findViewById(R.id.selected_pers_num);
        showAllUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_all);
        showAllUsers.setOnClickListener(this);
        showOnlineUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_online);
        showOnlineUsers.setOnClickListener(this);
        showOfflineUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_offline);
        showOfflineUsers.setOnClickListener(this);
        showSelectedUsers = (LinearLayout) view.findViewById(R.id.show_orgzation_selected);
        userLocation = (ImageView) view.findViewById(R.id.user_location);
        trajectory_btn = (ImageView) view.findViewById(R.id.trajectory_btn);
        talk = (ImageView) view.findViewById(R.id.talk);
        trajectory_btn.setOnClickListener(this);
        userLocation.setOnClickListener(this);
        showSelectedUsers.setOnClickListener(this);
        talk.setOnClickListener(this);
        fragPresen = new OrganFragmentPresenter(this);
    }

    //所有人员，在线，离线，已勾选点击事件
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.show_orgzation_all:
                mDatas.clear();

                onlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                offlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                selectedPersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                /*if(checkedNode.size()==0){
                    fragPresen.showAll(all);
                }*/
                fragPresen.showAll(all);
                break;
            case R.id.show_orgzation_online:
                onlinePersNum.setTextColor(getResources().getColor(R.color.title_bg_red));
                offlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                selectedPersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                /*if(checkedNode.size()==0){
                    fragPresen.showOnlineUser(ON_LINE);
                }*/
                fragPresen.showOnlineUser(ON_LINE);
                break;
            case R.id.show_orgzation_offline:
                onlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                offlinePersNum.setTextColor(getResources().getColor(R.color.title_bg_red));
                selectedPersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                /*if(checkedNode.size()==0){
                    fragPresen.showOfflineUser(OFF_LINE);
                }*/
                fragPresen.showOfflineUser(OFF_LINE);
                break;
            case R.id.show_orgzation_selected:
                onlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                offlinePersNum.setTextColor(getResources().getColor(R.color.dark_grey));
                selectedPersNum.setTextColor(getResources().getColor(R.color.title_bg_red));
                setCheckAdapter(NodeAddDelet,checkDatas);
                break;
            case R.id.user_location:
                initLatLng();
                List<String> userLoactionsIds = new ArrayList<>();
                for(String id:locationUserIds){
                    userLoactionsIds.add(id);
                }
                if(userLoactionsIds.size()!=0){
                    mainActivityPresenter.showLocations(userLoactionsIds);
                }
                break;

            case R.id.talk:
                //FIXME:暂时使用假数据
                List<String> userIds = new ArrayList<String>();
                userIds.add("500006");
                List<String> usermultiMediaIds = new ArrayList<>();
                for(String id:multiMediaUserID){
                    usermultiMediaIds.add(id);
                }
                mainActivityPresenter.showChatkRoom(usermultiMediaIds, new ArrayList<>(), RoomMode.NORMAL);
                break;
            case R.id.trajectory_btn:
                if(allLocaSubcription!=null) allLocaSubcription.dispose();
                if(singleSubcription!=null) singleSubcription.dispose();
                mainActivityPresenter.showHistoryTraceDialog();
                break;
        }
    }

    private Disposable singleSubcription;
    private Disposable allLocaSubcription;
    //多人定位时时刷新
    private void getLocationData(List<String> userLoactionsIds ){
        mainActivityPresenter.showLocations(userLoactionsIds);
//        allLocaSubcription = (Disposable) Observable.interval(10,10, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<Long>() {
//            @Override
//            public void accept(@NonNull Long ongs) throws Exception {
//                mainActivityPresenter.showLocations(userLoactionsIds);
//            }
//        });
    }


    //单人定位时时刷新
    private void getSingleLocationData(List<String> userLoactionsIds ){
        mainActivityPresenter.showLocation(userLoactionsIds);
//        singleSubcription = (Disposable) Observable.interval(10,10, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<Long>() {
//            @Override
//            public void accept(@NonNull Long Longs) throws Exception {
//                mainActivityPresenter.showLocation(userLoactionsIds);
//            }
//        });
    }

    private void initLatLng(){
        locations.add(new LatLng(30.664214,104.074297));
        locations.add(new LatLng(30.663197,104.069231));
        locations.add(new LatLng(30.662498,104.07401));
        locations.add(new LatLng(30.663888,104.076552));
        locations.add(new LatLng(30.662288,104.070704));
    }
    //通过presenter拿到所有数据回调展示
    @Override
    public void showAll(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize) {
        checkDatas.clear();
        for (OrgNodeBean node:list) {
            for(Node bean:NodeAddDelet){
                if(node.get_id().equals(bean.get_id())){
                    node.setChecked(true);
                    break;
                }
            }
            checkDatas.add(node);
        }
        setAdapterData(list,contactUserSize,onLineUserSize);
        setTVText(contactUserSize,onLineUserSize);
    }

    private void setTVText(int contactUserSize, int onLineUserSize){
        allUserNum.setText("("+contactUserSize+")");
        onlinePersNum.setText("在线("+onLineUserSize+")");
        offlinePersNum.setText("离线("+(contactUserSize - onLineUserSize)+")");
    }
    //初始化适配器
    private void initAdapter(ListView treeLv, List<OrgNodeBean> mDatas, boolean isHide) {
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
//                    locations.clear();
                    String locationUserId = node.get_id();
                    List<String> singleUserID = new ArrayList<String>();
                    singleUserID.add(locationUserId);
//                    locations.add(new LatLng(30.664214,104.074297));
                    getSingleLocationData(singleUserID);
                }
            }

            @Override
            public void onCheckChange(Node node, int position,
                                      List<Node> checkedNodes) {
                // TODO Auto-generated method stub
                NodeAddDelet = checkedNodes;
                locationUserIds.clear();
                int num = 0;
                for (Node bean:checkedNodes){
                    if(bean.isLeaf()){
                        num++;
                        if(!bean.isGroup()){
                            locationUserIds.add(bean.get_id());
                        }
                        multiMediaUserID.add(bean.get_id());
                    }
                }
                selectedPersNum.setText("已选("+num+")");
                if(!node.isChecked()&&node.isLeaf()){
                    locationUserIds.remove(node.get_id());
                    multiMediaUserID.remove(node.get_id());
                }
//                if(mainActivityPresenter.getLayoutVisibilily()==View.VISIBLE){
                List<Node> checkdata = new ArrayList<Node>();
                for(Node bean :checkedNodes){
                    if(bean.isLeaf()){
                        try {
                            checkdata.add((Node)bean.cloneNode());
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                    mainActivityPresenter.sendCheckedUsers(checkdata);
               /* if(NodeAddDelet.contains(node)){
                    if(!node.isChecked()){
                        NodeAddDelet.remove(node);
                    }
                }
                for (Node n : checkedNodes) {
                    if(NodeAddDelet.contains(n)){
                        if(NodeAddDelet.get(NodeAddDelet.indexOf(n)).isChecked()!= n.isChecked()){
                            NodeAddDelet.get(NodeAddDelet.indexOf(n)).setChecked(n.isChecked());
                        };
                    }else {
                        NodeAddDelet.add(n);
                    }
                }*/
            }

        });
        treeLv.setAdapter(adapter);
    }

    //通过presenter拿到在线用户数据回调展示
    @Override
    public void showOnLine(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize) {
        setAdapterData(list,contactUserSize,onLineUserSize);
    }

    //通过presenter拿到离线用户数据回调展示
    @Override
    public void showOffline(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize) {
        setAdapterData(list,contactUserSize,onLineUserSize);
    }


    //勾选适配器数据展示
    private void setCheckAdapter(List<Node> NodeAddDelet,List<OrgNodeBean> checkDatas){
        List<OrgNodeBean> checkData = new ArrayList<>();
        int num =0;
        for (OrgNodeBean node:checkDatas) {
            for(Node bean:NodeAddDelet){
                if(node.get_id().equals(bean.get_id())){
                    node.setChecked(true);
                    if(!checkData.contains(node)){
                        checkData.add(node);
                    }
                }
                if(bean.getFather() == null && bean.get_id().equals(node.get_id())){
                    if (!checkData.contains(node)){
                        checkData.add(node);
                    }
                }else if(bean.getFather() != null && bean.getFather().equals(node.get_id())){
                    if (!checkData.contains(node)){
                        checkData.add(node);
                    }
                }
            }
        }

        for (Node bean:NodeAddDelet){
            if(bean.isLeaf()){
                num++;
            }
        }
        if(adapter == null){
            initAdapter(treeLv,checkData,isHide);
            adapter.notifyDataSetChanged();
        }else {
            try {
                adapter.setDatas(checkData);
                adapter.notifyDataSetChanged();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        selectedPersNum.setText("已选("+num+")");
    }
    private void setAdapterData(List<OrgNodeBean> list,int contactUserSize, int onLineUserSize){
        mDatas.clear();
        for (OrgNodeBean node:list) {
            for(Node bean:NodeAddDelet){
                if(node.get_id().equals(bean.get_id())){
                    node.setChecked(true);
                    break;
                }
            }
            mDatas.add(node);
        }

        if(adapter == null){
                initAdapter(treeLv,mDatas,isHide);
                adapter.notifyDataSetChanged();
        }else {
            try {
                adapter.setDatas(mDatas);
                adapter.notifyDataSetChanged();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        setTVText(contactUserSize,onLineUserSize);
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

    @Override
    public String toString() {
        return super.toString();
    }

    //销毁时需要结束RXjava接收
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dposable != null) {
            dposable.dispose();
        }

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
}
