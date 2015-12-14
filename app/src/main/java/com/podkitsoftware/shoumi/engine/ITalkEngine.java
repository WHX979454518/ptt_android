package com.podkitsoftware.shoumi.engine;

import com.podkitsoftware.shoumi.service.signal.Room;

/**
 *
 * 定义对讲引擎操作的接口
 *
 * Created by fanchao on 13/12/15.
 */
public interface ITalkEngine {
    void connect(Room room);
    void dispose();
    void startSend();
    void stopSend();
}
