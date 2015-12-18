package com.xianzhitech.ptt.engine;

import android.support.annotation.NonNull;

/**
 * 创建语音引擎的工厂类
 *
 * Created by fanchao on 13/12/15.
 */
public interface TalkEngineProvider {
    @NonNull TalkEngine createEngine();
}
