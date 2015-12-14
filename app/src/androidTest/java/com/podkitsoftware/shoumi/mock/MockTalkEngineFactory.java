package com.podkitsoftware.shoumi.mock;

import android.content.Context;

import com.podkitsoftware.shoumi.engine.ITalkEngine;
import com.podkitsoftware.shoumi.engine.ITalkEngineFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockTalkEngineFactory implements ITalkEngineFactory {
    public final List<MockTalkEngine> aliveEngines = new ArrayList<>();

    @Override
    public ITalkEngine createEngine(final Context context) {
        final MockTalkEngine engine = new MockTalkEngine(context, talkEngine -> {
            aliveEngines.remove(talkEngine);
            return null;
        });

        aliveEngines.add(engine);
        return engine;
    }
}
