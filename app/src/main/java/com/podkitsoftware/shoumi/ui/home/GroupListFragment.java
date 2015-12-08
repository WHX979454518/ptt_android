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

import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.ui.base.BaseFragment;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * 显示会话列表(Group)的界面
 */
public class GroupListFragment extends BaseFragment {

    @Bind(R.id.groupList_recyclerView)
    RecyclerView listView;
    private final Adapter adapter = new Adapter();

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

    private class Adapter extends RecyclerView.Adapter<GroupItemHolder> {
        @Override
        public GroupItemHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            return new GroupItemHolder(parent);
        }

        @Override
        public void onBindViewHolder(final GroupItemHolder holder, final int position) {

        }

        @Override
        public int getItemCount() {
            return 0;
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

        public void setGroup(final Group group, final Collection<String> memberNames, final boolean hasMoreMembers) {
            nameView.setText(group.getName());
            Picasso.with(itemView.getContext())
                    .load(group.getImageUri())
                    .fit()
                    .into(iconView);

            memberView.setText(itemView.getResources().getString(hasMoreMembers ? R.string.group_member_with_more : R.string.group_member,
                    StringUtils.join(memberNames, itemView.getResources().getString(R.string.member_separator))));
        }
    }
}
