package com.podkitsoftware.shoumi.ui.base;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.View;

import com.trello.rxlifecycle.FragmentEvent;
import com.trello.rxlifecycle.RxLifecycle;

import rx.Observable;
import rx.subjects.BehaviorSubject;

public abstract class BaseFragment<T> extends Fragment {
    public final BehaviorSubject<FragmentEvent> lifecycleEventSubject = BehaviorSubject.create();
    protected T callbacks;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lifecycleEventSubject.onNext(FragmentEvent.CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();

        lifecycleEventSubject.onNext(FragmentEvent.RESUME);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lifecycleEventSubject.onNext(FragmentEvent.CREATE_VIEW);
    }

    @Override
    public void onStart() {
        super.onStart();

        lifecycleEventSubject.onNext(FragmentEvent.START);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        lifecycleEventSubject.onNext(FragmentEvent.ATTACH);

        if (getParentFragment() != null) {
            callbacks = (T)getParentFragment();
        }
        else {
            callbacks = (T) getActivity();
        }
    }

    @Override
    public void onDetach() {
        lifecycleEventSubject.onNext(FragmentEvent.DETACH);

        callbacks = null;
        super.onDetach();
    }

    @Override
    public void onStop() {
        lifecycleEventSubject.onNext(FragmentEvent.STOP);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        lifecycleEventSubject.onNext(FragmentEvent.DESTROY_VIEW);
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        lifecycleEventSubject.onNext(FragmentEvent.PAUSE);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        lifecycleEventSubject.onNext(FragmentEvent.DESTROY);
        super.onDestroy();
    }

    public <D> Observable.Transformer<? super D, ? extends D> bindToLifecycle() {
        return RxLifecycle.bindFragment(lifecycleEventSubject);
    }

    public <D> Observable.Transformer<? super D, ? extends D> bindUntil(FragmentEvent event) {
        return RxLifecycle.bindUntilFragmentEvent(lifecycleEventSubject, event);
    }
}
