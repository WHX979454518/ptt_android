package com.podkitsoftware.shoumi.mock;

import android.content.Context;

import com.podkitsoftware.shoumi.engine.TalkEngine;
import com.podkitsoftware.shoumi.model.Room;

import rx.subjects.PublishSubject;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockTalkEngine implements TalkEngine {
    public MockTalkEngine(final Context context) {
        this.room = room;
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
