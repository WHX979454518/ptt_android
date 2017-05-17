package com.zrt.ptt.app.console.viewmodel;

import android.databinding.ObservableField;

import com.xianzhitech.ptt.viewmodel.LifecycleViewModel;

/**
 * Created by hefei on 2017/5/14.
 */

public class ChatRoomViewModel extends LifecycleViewModel  {
    public final ObservableField<String> roomName = new ObservableField<>("");


    public ChatRoomViewModel(){
    }

//    public void setRoomName(String name){
//        roomName.set(name);
//    }
//
//    public String getRoomName(){
//        return roomName.get();
//    }
}
