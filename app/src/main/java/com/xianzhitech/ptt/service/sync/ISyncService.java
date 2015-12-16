package com.xianzhitech.ptt.service.sync;

/**
 * 同步数据的接口
 *
 * Created by fanchao on 15/12/15.
 */
public interface ISyncService {
    String ACTION_SYNC = "action_sync";

    void stopSync();
}
