package com.podkitsoftware.shoumi.engine;

import android.content.Context;

import com.podkitsoftware.shoumi.model.Room;

/**
 * 创建语音引擎的工厂类
 *
 * Created by fanchao on 13/12/15.
 */
public interface TalkEngineFactory {
    TalkEngine createEngine(final Context context, Room room);
}
