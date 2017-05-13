package com.zrt.ptt.app.console.mvp.view.fragment;


import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import utils.LogUtils;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.broker.SignalBroker;
import com.xianzhitech.ptt.data.Room;
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment;
import com.xianzhitech.ptt.ui.walkie.WalkieRoomFragment;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * A simple {@link Fragment} subclass.
 */
public class SystemStateFragment extends Fragment implements RoomListFragment.Callbacks, WalkieRoomFragment.Callbacks {
    private static final String TAG = "SystemStateFragment";

    private ListView treeLv;
    private Button checkSwitchBtn;
    private MyTreeListViewAdapter<OrgNodeBean> adapter;
    private List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;
    private RoomListFragment roomListFragment;

    private WalkieRoomFragment walkieRoomFragment;


    public SystemStateFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_system_state, container, false);

        super.onCreate(savedInstanceState);
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (roomListFragment == null) {
            roomListFragment = new RoomListFragment();
            ft.add(R.id.roomlist_fragment, roomListFragment);
        } else {
            ft.show(roomListFragment);
        }

        ft.commitAllowingStateLoss();
        /*initDatas();
        treeLv = (ListView) view.findViewById(R.id.sys_state_tree_lv);

        try {
            adapter = new MyTreeListViewAdapter<OrgNodeBean>(treeLv, getActivity(),
                    mDatas, 10, isHide);

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
                    *//*for (Node n : checkedNodes) {
                        int pos = n.getId() - 1;
                        sb.append(mDatas.get(pos).getName()).append("---")
                                .append(pos + 1).append(";");

                    }

                    Toast.makeText(getActivity().getApplicationContext(), sb.toString(),
                            Toast.LENGTH_SHORT).show();*//*
                }

            });
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        treeLv.setAdapter(adapter);*/



        return view;

    }

    private void initDatas() {
       /* mDatas.add(new SysStateNodeBean(1, 0, "中国古代"));
        mDatas.add(new SysStateNodeBean(2, 1, "唐朝"));
        mDatas.add(new SysStateNodeBean(3, 1, "宋朝"));
        mDatas.add(new SysStateNodeBean(4, 1, "明朝"));
        mDatas.add(new SysStateNodeBean(5, 2, "李世民"));
        mDatas.add(new SysStateNodeBean(6, 2, "李白"));

        mDatas.add(new SysStateNodeBean(7, 3, "赵匡胤"));
        mDatas.add(new SysStateNodeBean(8, 3, "苏轼"));

        mDatas.add(new SysStateNodeBean(9, 4, "朱元璋"));
        mDatas.add(new SysStateNodeBean(10, 4, "唐伯虎"));
        mDatas.add(new SysStateNodeBean(11, 4, "文征明"));
        mDatas.add(new SysStateNodeBean(12, 7, "赵建立"));
        mDatas.add(new SysStateNodeBean(13, 8, "苏东东"));
        mDatas.add(new SysStateNodeBean(14, 10, "秋香"));
        mDatas.add(new SysStateNodeBean(15, 0, "美国古代"));
        mDatas.add(new SysStateNodeBean(16, 15, "美国西部世界"));
        mDatas.add(new SysStateNodeBean(17, 15, "美国东世界"));
        mDatas.add(new SysStateNodeBean(18, 16, "西部世界华盛顿"));
        mDatas.add(new SysStateNodeBean(19, 16, "西部世界芝加哥"));
        mDatas.add(new SysStateNodeBean(20, 17, "东部世界洛杉矶"));
        mDatas.add(new SysStateNodeBean(21, 17, "东部世界纽约"));*/
        String json = "{\"status\":200,\"data\":[{\"lastLocationTimeCST\":\"\",\"positionname\":\"总经理\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcogiAYgUMAABsrdgKnKM450.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec69ff3ca2d0007beb0e7\",\"commander\":1,\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"phoneNbr\":11122233344,\"idNumber\":9100001,\"name\":\"指挥号\",\"__v\":0,\"position\":\"59014861f3ca2d0007beb151\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"mail\":\"\",\"privileges\":{\"priority\":null,\"viewMap\":false,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec91df3ca2d0007beb0ec\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"五杀\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"asd@dom.com\",\"idNumber\":9100002,\"phoneNbr\":13500000000,\"__v\":0,\"position\":\"59014876f3ca2d0007beb152\",\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901491ff3ca2d0007beb156\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"流浪汉\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"59014876f3ca2d0007beb152\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"aaad@dom.com\",\"idNumber\":9100003,\"phoneNbr\":13566666666,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"平民\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcoqaAXNIPAABq9HszdDo314.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901495af3ca2d0007beb157\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"打手\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"590148a2f3ca2d0007beb154\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"adf@dom.com\",\"idNumber\":9100004,\"phoneNbr\":13522222222,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"_id\":\"58fec8a1f3ca2d0007beb0eb\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"青铜\",\"__v\":0},{\"_id\":\"59014806f3ca2d0007beb150\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"撸撸撸\",\"__v\":0},{\"father\":-1,\"name\":\"czqtest\",\"_id\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true}]}";

        Gson gson = new Gson();
        JSONObject data = null;
        try {
            data = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

//        JSONObject data = gson.fromJson(json,JSONObject.class);
        if (data.has("data")) {

            try {
//                String datas = data.getJSONArray("data");
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

    public void showChatRoomView(List<String> usersIds, List<String> groupIds, boolean isVideoChat) {
        Log.d(TAG, "showChatRoomView() called with: usersIds = [" + usersIds + "], groupIds = [" + groupIds + "], isVideoChat = [" + isVideoChat + "]");

        SignalBroker signalBroker = ((AppComponent) App.getInstance()).getSignalBroker();
        signalBroker.createRoom(usersIds, groupIds)
                .doOnSuccess(new Consumer<Room>() {
                    @Override
                    public void accept(@NonNull Room room) throws Exception {
                        joinRoom(room.getId(), false, isVideoChat);
                    }
                })
                .subscribe();
    }

    public void showChatRoomView(String roomId, boolean fromInvitation, boolean isVideoChat) {
        joinRoom(roomId, fromInvitation, isVideoChat);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement WalkieRoomFragment.Callbacks interface ////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void navigateToChatRoomPage(@NotNull Room room) {
        Log.d(TAG, "navigateToChatRoomPage() called with: room = [" + room + "]");
        joinRoom(room.getId(), false, false);
    }

    @Override
    public void navigateToCreateRoomMemberSelectionPage() {
        Log.d(TAG, "navigateToCreateRoomMemberSelectionPage() called");
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement WalkieRoomFragment.Callbacks interface ////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void navigateToRoomMemberPage(String roomId)
    {
        LogUtils.d(TAG, "navigateToRoomMemberPage() called with: roomId = [" + roomId + "]");
    }

    @Override
    public void navigateToUserDetailsPage(String roomId){
        LogUtils.d(TAG, "navigateToUserDetailsPage() called with: roomId = [" + roomId + "]");
    }


    ////////////////////////////////////////////////////////////////////////////////
    ///////////////////////Private Inner help methods///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////
    private void joinRoom(String roomId,  boolean fromInvitation, boolean isVideoChat) {
        LogUtils.d(TAG, "joinRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "], isVideoChat = [" + isVideoChat + "]");

        Bundle bundle = new Bundle();
        bundle.putString(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_ID, roomId);
        bundle.putBoolean(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_FROM_INVITATION, fromInvitation);

        //FIXME://当前写死WalkieRoomFragment未考虑isVideoChat
        if (walkieRoomFragment == null) {
            walkieRoomFragment = new WalkieRoomFragment();
        }

        walkieRoomFragment.setArguments(bundle);

        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if(walkieRoomFragment.getParentFragment() == null){
            ft.add(R.id.chatroom_fragment, walkieRoomFragment);
        }
        else {
            ft.show(walkieRoomFragment);
        }

        ft.commitAllowingStateLoss();
    }
}
