package com.zrt.ptt.app.console.mvp.view.fragment;


import kotlin.Pair;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.FragmentManagerUtils;
import utils.LogUtils;

import android.os.Bundle;
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
import com.xianzhitech.ptt.ui.base.BaseActivity;
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment;
import com.xianzhitech.ptt.ui.call.CallFragment;
import com.xianzhitech.ptt.ui.chat.ChatFragment;
import com.xianzhitech.ptt.ui.walkie.WalkieRoomFragment;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.databinding.FragmentChatRoomBinding;
import com.zrt.ptt.app.console.viewmodel.ChatRoomViewModel;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;


/**
 * A simple {@link Fragment} subclass.
 */

public class ChatRoomFragment extends BaseViewModelFragment<ChatRoomViewModel, FragmentChatRoomBinding>
        implements WalkieRoomFragment.Callbacks, ChatFragment.Callbacks, CallFragment.Callbacks, ChatRoomViewModel.Callbacks{
    private static final String TAG = "ChatRoomFragment";

    private WalkieRoomFragment walkieRoomFragment;
    private CallFragment callFragment;


    //Normal--文字聊天类型Fragment
    private ChatFragment chatFragment;
    private Disposable chatRoomTitleDisposable;
    private Disposable callRoomTitleDisposable;


    public ChatRoomFragment() {
    }


    ////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////Public methods////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////
    public void joinRoom(String roomId, boolean fromInvitation, RoomMode roomMode){
        LogUtils.d(TAG, "joinRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "], roomMode = [" + roomMode + "]");

        switch (roomMode){
            case NORMAL:
                joinNormalRoom(roomId, fromInvitation);
                break;
            case AUDIO:
                joinVideoOrAudioRoom(roomId, fromInvitation, true);
                break;
            case VIDEO:
                joinVideoOrAudioRoom(roomId, fromInvitation, false);
                break;
            case Conversion:
                joinChatRoom(roomId);
                break;
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
        ChatRoomViewModel viewModel = new ChatRoomViewModel(this);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement ChatFragment.Callbacks interface //////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void navigateToWalkieTalkiePage(@NotNull String roomId) {
        Log.d(TAG, "navigateToWalkieTalkiePage() called with: roomId = [" + roomId + "]");
        if(getActivity() instanceof BaseActivity)
        {
            ((BaseActivity)getActivity()).navigateToWalkieTalkiePage(roomId);
        }
    }

    @Override
    public void navigateToWalkieTalkiePage() {
        Log.d(TAG, "navigateToWalkieTalkiePage() called");

        if(getActivity() instanceof BaseActivity)
        {
            ((BaseActivity)getActivity()).navigateToWalkieTalkiePage();
        }
    }

    @Override
    public void navigateToVideoChatPage() {
        Log.d(TAG, "navigateToVideoChatPage() called");

        if(getActivity() instanceof BaseActivity)
        {
            ((BaseActivity)getActivity()).navigateToVideoChatPage();
        }
    }

    @Override
    public void navigateToVideoChatPage(@NotNull String roomId, boolean audioOnly) {
        Log.d(TAG, "navigateToVideoChatPage() called with: roomId = [" + roomId + "], audioOnly = [" + audioOnly + "]");

        if(getActivity() instanceof BaseActivity)
        {
            ((BaseActivity)getActivity()).navigateToVideoChatPage(roomId, audioOnly);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement CallFragment.Callbacks interface //////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void closeCallPage() {
        Log.d(TAG, "closeCallPage() called");
        FragmentManagerUtils.removeFragment(this, callFragment);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////Implement ChatRoomViewModel.Callbacks interface /////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCloseCallRoom() {
        FragmentManagerUtils.removeFragment(this, callFragment);
        FragmentManagerUtils.removeFragment(this, walkieRoomFragment);
        getViewModel().callRoomTitleNameVisible.set(false);
    }

    @Override
    public void onCloseChatRoom() {
        FragmentManagerUtils.removeFragment(this, chatFragment);
        getViewModel().chatRoomTitleNameVisible.set(false);
    }

    @Override
    public void onShowChatRoom() {
        FragmentManagerUtils.replaceFragment(this, chatFragment, R.id.chatroom_fragment_placeholder);
    }

    @Override
    public void onShowCallRoom() {
        if(callFragment!= null && !callFragment.isAdded()){
            FragmentManagerUtils.replaceFragment(this, callFragment, R.id.chatroom_fragment_placeholder);
        }

        if(walkieRoomFragment != null && !walkieRoomFragment.isAdded()){
            FragmentManagerUtils.replaceFragment(this, walkieRoomFragment, R.id.chatroom_fragment_placeholder);
        }
    }



    ////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////Inner help methods//////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 进入文字聊天室
     * @param roomId
     */
    private void joinChatRoom(String roomId){
        Log.d(TAG, "joinChatRoom() called with: roomId = [" + roomId + "]");
        if (chatFragment != null) {
            FragmentManagerUtils.removeFragment(this, chatFragment);
        }

        chatFragment = ChatFragment.Companion.createInstance(roomId);
        FragmentManagerUtils.replaceFragment(this, chatFragment, R.id.chatroom_fragment_placeholder);

        //设置房间标题
        if(chatRoomTitleDisposable != null)
            chatRoomTitleDisposable.dispose();

        chatRoomTitleDisposable = ((AppComponent) (App.getInstance())).getStorage().getRoomWithName(roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Optional<Pair<Room, String>>>() {
                    @Override
                    public void accept(@NonNull Optional<Pair<Room, String>> pairOptional) throws Exception {
                        Log.d(TAG, "ChatRoomFragment.roomTitle = [" + pairOptional.orNull().getSecond() + "]");
                        getViewModel().callRoomTitleName.set(pairOptional.orNull().getSecond());
                    }
                });

        getViewModel().chatRoomTitleNameVisible.set(true);
    }

    /**
     * 加入语音类型的聊天或视频聊天
     * @param roomId
     * @param fromInvitation
     */
    private void joinVideoOrAudioRoom(String roomId, boolean fromInvitation, boolean audioOnly){
        Log.d(TAG, "joinVideoOrAudioRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "]");
        Bundle bundle = new Bundle();
        bundle.putString(CallFragment.ARG_JOIN_ROOM_ID, roomId);
        bundle.putBoolean(CallFragment.ARG_JOIN_ROOM_AUDIO_ONLY, audioOnly);
        if (callFragment != null) {
            FragmentManagerUtils.removeFragment(this, callFragment);
        }

        callFragment = new CallFragment();
        callFragment.setArguments(bundle);
        FragmentManagerUtils.replaceFragment(this, callFragment, R.id.chatroom_fragment_placeholder);

        setupVideoOrAudioRoomTitle(roomId);

    }

    /**
     * 加入普通
     * @param roomId
     */
    private void joinNormalRoom(String roomId, boolean fromInvitation){
        Log.d(TAG, "joinNormalRoom() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "]");
        Bundle bundle = new Bundle();
        bundle.putString(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_ID, roomId);
        bundle.putBoolean(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_FROM_INVITATION, fromInvitation);
        if (walkieRoomFragment != null) {
            FragmentManagerUtils.removeFragment(this, walkieRoomFragment);
        }

        walkieRoomFragment = new WalkieRoomFragment();
        walkieRoomFragment.setArguments(bundle);
        FragmentManagerUtils.replaceFragment(this, walkieRoomFragment, R.id.chatroom_fragment_placeholder);

        getViewModel().callRoomTitleNameVisible.set(true);
    }


    private void setupVideoOrAudioRoomTitle(String roomId){
        //设置房间标题
        if(callRoomTitleDisposable != null)
            callRoomTitleDisposable.dispose();

        callRoomTitleDisposable = ((AppComponent) (App.getInstance())).getStorage().getRoomWithName(roomId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Optional<Pair<Room, String>>>() {
                    @Override
                    public void accept(@NonNull Optional<Pair<Room, String>> pairOptional) throws Exception {
                        Log.d(TAG, "ChatRoomFragment.roomTitle = [" + pairOptional.orNull().getSecond() + "]");
                        getViewModel().callRoomTitleName.set(pairOptional.orNull().getSecond());
                    }
                });

        getViewModel().callRoomTitleNameVisible.set(true);
    }
}
