package com.podkitsoftware.shoumi.mock;

import android.content.Context;

import com.podkitsoftware.shoumi.engine.TalkEngine;
import com.podkitsoftware.shoumi.engine.TalkEngineFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockTalkEngineFactory implements TalkEngineFactory {
    public final List<MockTalkEngine> createdEngines = new ArrayList<>();

    @Override
    public TalkEngine createEngine(final Context context) {
        final MockTalkEngine engine = new MockTalkEngine(context);
        createdEngines.add(engine);
        return engine;
    }
}
