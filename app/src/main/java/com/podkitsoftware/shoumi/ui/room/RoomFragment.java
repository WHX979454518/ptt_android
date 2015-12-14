package com.podkitsoftware.shoumi.ui.room;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.podkitsoftware.shoumi.AppComponent;
import com.podkitsoftware.shoumi.Broker;
import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.Person;
import com.podkitsoftware.shoumi.ui.base.BaseFragment;
import com.podkitsoftware.shoumi.ui.util.ResourceUtil;
import com.podkitsoftware.shoumi.ui.widget.PushToTalkButton;

import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

/**
 * 显示对话界面
 *
 * Created by fanchao on 11/12/15.
 */
public class RoomFragment extends BaseFragment<RoomFragment.Callbacks> {

    public static final String ARG_ROOM_ID = "arg_room_id";

    @Bind(R.id.room_toolbar)
    Toolbar toolbar;

    @Bind(R.id.room_pushToTalkButton)
    PushToTalkButton pttBtn;

    @Bind(R.id.room_appBar)
    ViewGroup appBar;
    
    Broker broker;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        broker = ((AppComponent) getActivity().getApplication()).providesBroker();
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

        final String groupId = getArguments().getString(ARG_ROOM_ID);
        Observable.combineLatest(
                broker.getGroup(groupId),
                broker.getGroupMembers(groupId),
                broker.getOnlineUsers(),
                Triple::of)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<Triple<Group, List<Person>, Set<String>>>bindToLifecycle())
                .subscribe(result -> {
                    int groupOnlineCount = 0;
                    final Set<String> onlineUsers = result.getRight();
                    for (final Person person : result.getMiddle()) {
                        if (onlineUsers.contains(person.getId())) {
                            groupOnlineCount++;
                        }
                    }

                    toolbar.setTitle(getString(R.string.room_title,
                            result.getLeft().getName(),
                            groupOnlineCount,
                            result.getMiddle().size()));
                });
    }

    public static Fragment create(final String roomId) {
        final RoomFragment fragment = new RoomFragment();
        final Bundle args = new Bundle(1);
        args.putString(ARG_ROOM_ID, roomId);
        fragment.setArguments(args);
        return fragment;
    }

    public interface Callbacks {
    }
}
