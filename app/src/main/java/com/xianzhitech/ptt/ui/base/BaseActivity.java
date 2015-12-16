package com.xianzhitech.ptt.ui.base;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;
import rx.Observable;
import rx.subjects.BehaviorSubject;

public abstract class BaseActivity extends AppCompatActivity {
    private final BehaviorSubject<ActivityEvent> lifecycleEventSubject = BehaviorSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lifecycleEventSubject.onNext(ActivityEvent.CREATE);
    }

    @Override
    protected void onDestroy() {
        lifecycleEventSubject.onNext(ActivityEvent.DESTROY);

        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();

        lifecycleEventSubject.onNext(ActivityEvent.START);
    }

    @Override
    protected void onStop() {
        lifecycleEventSubject.onNext(ActivityEvent.STOP);

        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        lifecycleEventSubject.onNext(ActivityEvent.RESUME);
    }

    @Override
    protected void onPause() {
        lifecycleEventSubject.onNext(ActivityEvent.PAUSE);

        super.onPause();
    }

    public <D> Observable.Transformer<? super D, ? extends D> bindToLifecycle() {
        return RxLifecycle.bindActivity(lifecycleEventSubject);
    }

    public <D> Observable.Transformer<? super D, ? extends D> bindUntil(ActivityEvent event) {
        return RxLifecycle.bindUntilActivityEvent(lifecycleEventSubject, event);
    }
}
