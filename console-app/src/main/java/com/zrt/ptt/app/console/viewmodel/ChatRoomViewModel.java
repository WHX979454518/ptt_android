package com.zrt.ptt.app.console.viewmodel;

import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.xianzhitech.ptt.viewmodel.LifecycleViewModel;
import com.zrt.ptt.app.console.R;

/**
 * Created by hefei on 2017/5/14.
 */

public class ChatRoomViewModel extends LifecycleViewModel  {
    private static final String TAG = "ChatRoomViewModel";

    public final ObservableField<String> chatRoomTitleName = new ObservableField<>("");
    public final ObservableField<String> callRoomTitleName = new ObservableField<>("");
   /* public final ObservableField<Integer> chatRoomBitmap = new ObservableField<>(R.drawable.chat);
    public final ObservableField<Integer> callRoomBitMap = new ObservableField<>(R.drawable.ic_call_black);*/
    public final ObservableBoolean chatRoomTitleNameVisible = new ObservableBoolean(false);
    public final ObservableBoolean callRoomTitleNameVisible = new ObservableBoolean(false);



    private Callbacks callbacks;


    public interface Callbacks{
        void onCloseCallRoom();
        void onCloseChatRoom();
        void onShowChatRoom();
        void onShowCallRoom();
    }

    public ChatRoomViewModel(Callbacks navigator){
        this.callbacks = navigator;
    }

    public void onClickChatRoomTitle(){
        Log.d(TAG, "onClickChatRoomTitle() called");

        callbacks.onShowChatRoom();
    }

    public void onClickCallRoomTitle()
    {
        Log.d(TAG, "onClickCallRoomTitle() called");

        callbacks.onShowCallRoom();
    }

    public void onClickCloseChatRoom(){
        Log.d(TAG, "onClickCloseChatRoom() called");
        callbacks.onCloseChatRoom();
    }

    public void onClickCloseCallRoom(){
        Log.d(TAG, "onClickCloseCallRoom() called");
        callbacks.onCloseCallRoom();
    }
}
