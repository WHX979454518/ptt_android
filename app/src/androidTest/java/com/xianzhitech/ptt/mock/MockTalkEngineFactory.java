package com.xianzhitech.ptt.mock;

import android.content.Context;

import com.xianzhitech.ptt.engine.TalkEngine;
import com.xianzhitech.ptt.engine.TalkEngineFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockTalkEngineFactory implements TalkEngineFactory {
    public final List<MockTalkEngine> aliveEngines = new ArrayList<>();

    @Override
    public TalkEngine createEngine(final Context context) {
        final MockTalkEngine engine = new MockTalkEngine(context, talkEngine -> {
            aliveEngines.remove(talkEngine);
            return null;
        });

        aliveEngines.add(engine);
        return engine;
    }
}
