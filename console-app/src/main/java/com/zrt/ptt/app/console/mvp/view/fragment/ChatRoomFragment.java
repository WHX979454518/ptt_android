package com.zrt.ptt.app.console.mvp.view.fragment;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.socket.client.On;
import kotlin.Pair;
import utils.DatabindingUtils;
import utils.FragmentManagerUtils;
import utils.LogUtils;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.broker.RoomMode;
import com.xianzhitech.ptt.data.Room;
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment;
import com.xianzhitech.ptt.ui.chat.ChatFragment;
import com.xianzhitech.ptt.ui.walkie.WalkieRoomFragment;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.databinding.FragmentChatRoomBinding;
import com.zrt.ptt.app.console.viewmodel.ChatRoomViewModel;


/**
 * A simple {@link Fragment} subclass.
 */


public class ChatRoomFragment extends BaseViewModelFragment<ChatRoomViewModel, FragmentChatRoomBinding> implements WalkieRoomFragment.Callbacks{
    private static final String TAG = "ChatRoomFragment";

    private WalkieRoomFragment walkieRoomFragment;

    //Normal--文字聊天类型Fragment
    private ChatFragment chatFragment;
    private Disposable titleDisposable;


    public ChatRoomFragment() {
    }


    ////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////
    public void joinRoom(String roomId, boolean fromInvitation, RoomMode roomMode){
        LogUtils.d(TAG, "joinRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "], roomMode = [" + roomMode + "]");

        switch (roomMode){
            case NORMAL:{
                joinNormalRoom(roomId);
                break;
            }

            case AUDIO:{
                joinAuidoRoom(roomId, fromInvitation);
                break;
            }

            default:
                LogUtils.e(TAG, "joinRoom: Unsupport roomMode = " + roomMode);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////Override Superclass BaseViewModelFragment methods///////
    ////////////////////////////////////////////////////////////////////////////////////////////
    @NotNull
    @Override
    public FragmentChatRoomBinding onCreateDataBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentChatRoomBinding.inflate(inflater, container, false);
    }

    @NotNull
    @Override
    public ChatRoomViewModel onCreateViewModel() {
        ChatRoomViewModel viewModel = new ChatRoomViewModel();
        return viewModel;
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

    @Override
    public void closeRoomPage() {
        LogUtils.d(TAG, "closeRoomPage() called");

        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.hide(walkieRoomFragment);
        ft.commitAllowingStateLoss();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////Inner help methods//////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 加入语音类型的聊天
     * @param roomId
     * @param fromInvitation
     */
    private void joinAuidoRoom(String roomId, boolean fromInvitation){
        Log.d(TAG, "joinAuidoRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "]");
        Bundle bundle = new Bundle();
        bundle.putString(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_ID, roomId);
        bundle.putBoolean(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_FROM_INVITATION, fromInvitation);
        if (walkieRoomFragment != null) {
            FragmentManagerUtils.removeFragment(this, walkieRoomFragment);
        }

        walkieRoomFragment.setArguments(bundle);
        FragmentManagerUtils.showFragment(this, walkieRoomFragment, R.id.chatroom_fragment_placeholder);
    }

    /**
     * 加入文字类型聊天
     * @param roomId
     */
    private void joinNormalRoom(String roomId){
        LogUtils.d(TAG, "joinNormalRoom() called with: roomId = [" + roomId + "]");
        Bundle bundle = new Bundle();
        bundle.putString(ChatFragment.ARG_ROOM_ID, roomId);
        if (chatFragment != null) {
            FragmentManagerUtils.removeFragment(this, chatFragment);
        }

        chatFragment = new ChatFragment();
        chatFragment.setArguments(bundle);

        FragmentManagerUtils.showFragment(this, chatFragment, R.id.chatroom_fragment_placeholder);

        //设置房间标题

        if(titleDisposable != null)
            titleDisposable.dispose();

        titleDisposable = ((AppComponent) (App.getInstance())).getStorage().getRoomWithName(roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Optional<Pair<Room, String>>>() {
                    @Override
                    public void accept(@NonNull Optional<Pair<Room, String>> pairOptional) throws Exception {
                        Log.d(TAG, "ChatRoomFragment.roomTitle = [" + pairOptional.orNull().getSecond() + "]");
                        getViewModel().roomName.set(pairOptional.orNull().getSecond());
                    }
                });
    }
}
