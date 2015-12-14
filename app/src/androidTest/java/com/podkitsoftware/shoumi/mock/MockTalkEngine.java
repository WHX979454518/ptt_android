package com.podkitsoftware.shoumi.mock;

import android.content.Context;

import com.podkitsoftware.shoumi.engine.ITalkEngine;
import com.podkitsoftware.shoumi.service.signal.Room;

import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockTalkEngine implements ITalkEngine {
    private final Func1<MockTalkEngine, Void> disposeRunnable;

    public MockTalkEngine(final Context context, final Func1<MockTalkEngine, Void> disposeRunnable) {
        this.disposeRunnable = disposeRunnable;
    }

    public enum Status {
        INITIALIZED,
        STARTED_SEND,
        DISPOSED,
    }

    public PublishSubject<Status> status = PublishSubject.create();
    public Room room;

    @Override
    public void connect(final Room room) {
        this.room = room;
    }

    @Override
    public void dispose() {
        status.onNext(Status.DISPOSED);
        if (disposeRunnable != null) {
            disposeRunnable.call(this);
        }
    }

    @Override
    public void startSend() {
        status.onNext(Status.STARTED_SEND);
    }

    @Override
    public void stopSend() {
        status.onNext(Status.INITIALIZED);
    }
}
