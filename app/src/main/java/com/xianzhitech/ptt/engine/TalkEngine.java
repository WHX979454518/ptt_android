package com.xianzhitech.ptt.engine;


import android.support.annotation.NonNull;

import com.xianzhitech.ptt.model.Room;

/**
 *
 * 定义对讲引擎操作的接口
 *
 * Created by fanchao on 13/12/15.
 */
public interface TalkEngine {
    void connect(@NonNull  Room room);
    void dispose();
    void startSend();
    void stopSend();
}
