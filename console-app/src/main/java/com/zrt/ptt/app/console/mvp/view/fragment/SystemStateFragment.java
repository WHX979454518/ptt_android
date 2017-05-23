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
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.broker.RoomMode;
import com.xianzhitech.ptt.broker.SignalBroker;
import com.xianzhitech.ptt.data.Room;
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;

import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;

import static com.xianzhitech.ptt.broker.RoomMode.Conversion;
import static com.xianzhitech.ptt.broker.RoomMode.NORMAL;

/**
 * A simple {@link Fragment} subclass.
 */
public class SystemStateFragment extends Fragment implements RoomListFragment.Callbacks {
    private static final String TAG = "SystemStateFragment";

    private ListView treeLv;
    private Button checkSwitchBtn;
    private MyTreeListViewAdapter<OrgNodeBean> adapter;
    private List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;
    private RoomListFragment roomListFragment;
    private ChatRoomFragment chatRoom;


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

        chatRoom = new ChatRoomFragment();
        ft.add(R.id.chatroom_fragment, chatRoom);

        ft.commitAllowingStateLoss();
        return view;

    }


    public void showChatRoomView(List<String> usersIds, List<String> groupIds, RoomMode roomMode) {
        Log.d(TAG, "showChatRoomView() called with: usersIds = [" + usersIds + "], groupIds = [" + groupIds + "], roomMode = [" + roomMode + "]");

        SignalBroker signalBroker = ((AppComponent) App.getInstance()).getSignalBroker();
        signalBroker.createRoom(usersIds, groupIds)
                .doOnSuccess(new Consumer<Room>() {
                    @Override
                    public void accept(@NonNull Room room) throws Exception {
                        joinRoom(room.getId(), false, roomMode);
                    }
                })
                .subscribe();
    }

    public void showChatRoomView(String roomId, boolean fromInvitation, RoomMode roomMode) {
        joinRoom(roomId, fromInvitation, roomMode);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement RoomListFragment.Callbacks interface ////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void navigateToChatRoomPage(@NotNull Room room) {
        Log.d(TAG, "navigateToChatRoomPage() called with: room = [" + room + "]");
        joinRoom(room.getId(), false, Conversion);
    }

    @Override
    public void navigateToCreateRoomMemberSelectionPage() {
        Log.d(TAG, "navigateToCreateRoomMemberSelectionPage() called");
    }

    ////////////////////////////////////////////////////////////////////////////////
    ///////////////////////Private Inner help methods///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////
    private void joinRoom(String roomId,  boolean fromInvitation, RoomMode roomMode) {
        LogUtils.d(TAG, "joinRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "], roomMode = [" + roomMode + "]");
        chatRoom.joinRoom(roomId, fromInvitation, roomMode);
    }
}
