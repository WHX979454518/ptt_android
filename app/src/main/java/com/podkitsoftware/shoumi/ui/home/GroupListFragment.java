package com.podkitsoftware.shoumi.ui.home;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.podkitsoftware.shoumi.AppComponent;
import com.podkitsoftware.shoumi.Broker;
import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.ui.base.BaseFragment;
import com.podkitsoftware.shoumi.ui.room.RoomActivity;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;

/**
 * 显示会话列表(Group)的界面
 */
public class GroupListFragment extends BaseFragment<Void> {

    private static final int MAX_MEMBER_TO_DISPLAY = 3;

    @Bind(R.id.groupList_recyclerView)
    RecyclerView listView;
    private final Adapter adapter = new Adapter();
    private Broker broker;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        broker = ((AppComponent) getActivity().getApplication()).providesBroker();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_group_list, container, false);
        ButterKnife.bind(this, view);

        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        listView.setAdapter(adapter);
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

        broker.getGroupsWithMemberNames(MAX_MEMBER_TO_DISPLAY)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<List<Broker.GroupInfo<String>>>bindToLifecycle())
                .subscribe(adapter::setGroups);
    }

    private class Adapter extends RecyclerView.Adapter<GroupItemHolder> {
        private final ArrayList<Broker.GroupInfo<String>> groups = new ArrayList<>();

        public void setGroups(final Collection<Broker.GroupInfo<String>> newGroups) {
            groups.clear();
            if (newGroups != null) {
                groups.addAll(newGroups);
            }
            notifyDataSetChanged();
        }


        @Override
        public GroupItemHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            return new GroupItemHolder(parent);
        }

        @Override
        public void onBindViewHolder(final GroupItemHolder holder, final int position) {
            holder.setGroup(groups.get(position));
            holder.itemView.setOnClickListener(v -> startActivity(RoomActivity.builder(getContext(), groups.get(position).group.getId())));
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }
    }

    static class GroupItemHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.groupListItem_members)
        TextView memberView;

        @Bind(R.id.groupListItem_name)
        TextView nameView;

        @Bind(R.id.groupListItem_icon)
        ImageView iconView;

        public GroupItemHolder(final ViewGroup container) {
            super(LayoutInflater.from(container.getContext()).inflate(R.layout.view_group_list_item, container, false));

            ButterKnife.bind(this, itemView);
        }

        public void setGroup(final Broker.GroupInfo<String> info) {
            nameView.setText(info.group.getName());
//            Picasso.with(itemView.getContext())
//                    .load(info.group.getImageUri())
//                    .fit()
//                    .into(iconView);

            memberView.setText(itemView.getResources().getString(info.memberCount > info.members.size() ? R.string.group_member_with_more : R.string.group_member,
                    StringUtils.join(info.members, itemView.getResources().getString(R.string.member_separator))));

        }
    }
}
