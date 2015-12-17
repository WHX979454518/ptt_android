package com.xianzhitech.ptt.ui.room;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.Broker;
import com.xianzhitech.ptt.R;
import com.xianzhitech.ptt.model.Conversation;
import com.xianzhitech.ptt.model.Room;
import com.xianzhitech.ptt.service.provider.CreateConversationRequest;
import com.xianzhitech.ptt.service.provider.SignalProvider;
import com.xianzhitech.ptt.ui.base.BaseFragment;
import com.xianzhitech.ptt.ui.util.ResourceUtil;
import com.xianzhitech.ptt.ui.widget.PushToTalkButton;

import java.util.Collections;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

/**
 * 显示对话界面
 *
 * Created by fanchao on 11/12/15.
 */
public class RoomFragment extends BaseFragment<RoomFragment.Callbacks> {

    public static final String ARG_ROOM_REQUEST = "arg_room_request";

    @Bind(R.id.room_toolbar)
    Toolbar toolbar;

    @Bind(R.id.room_pushToTalkButton)
    PushToTalkButton pttBtn;

    @Bind(R.id.room_appBar)
    ViewGroup appBar;

    @Bind(R.id.room_progress)
    View progressBar;

    Broker broker;

    SignalProvider signalProvider;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AppComponent component = (AppComponent) getActivity().getApplication();
        broker = component.providesBroker();
        signalProvider = component.providesSignal();
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_room, container, false);
        ButterKnife.bind(this, view);
        toolbar.setNavigationIcon(ResourceUtil.getTintedDrawable(getContext(), R.drawable.ic_arrow_back, Color.WHITE));
        toolbar.setNavigationOnClickListener(v -> getActivity().finish());
        ViewCompat.setElevation(pttBtn, ViewCompat.getElevation(appBar) + getResources().getDimension(R.dimen.divider_normal));
        return view;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();

        final OpenRoomRequest request = getArguments().getParcelable(ARG_ROOM_REQUEST);

        if (request != null) {
            Observable<String> conversationIdObservable;

            if (request.conversationId == null) {
                // 没有会话id, 向服务器请求
                final CreateConversationRequest createRequest;
                if (request.groupId != null) {
                    createRequest = CreateConversationRequest.fromGroup(request.groupId);
                }
                else if (request.personId != null) {
                    createRequest = CreateConversationRequest.fromPerson(request.personId);
                }
                else {
                    throw new IllegalArgumentException();
                }

                conversationIdObservable = signalProvider.createConversation(Collections.singleton(createRequest))
                        .flatMap(broker::saveConversation)
                        .map(Conversation::getId);
            }
            else {
                conversationIdObservable = Observable.just(request.conversationId);
            }

            conversationIdObservable
                    .flatMap(signalProvider::joinConversation)
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.<Room>bindToLifecycle())
                    .subscribe(new Subscriber<Room>() {
                        @Override
                        public void onCompleted() {
                            progressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onError(final Throwable e) {
                            new AlertDialog.Builder(getContext())
                                    .setMessage("Joined room failed: " + e.getMessage())
                                    .create()
                                    .show();
                            progressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onNext(final Room room) {
                            new AlertDialog.Builder(getContext())
                                    .setMessage("Joined room: " + room)
                                    .create()
                                    .show();
                        }
                    });
        }
    }

    public static Fragment create(final OpenRoomRequest request) {
        final RoomFragment fragment = new RoomFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(ARG_ROOM_REQUEST, request);
        fragment.setArguments(args);
        return fragment;
    }

    public interface Callbacks {
    }
}
