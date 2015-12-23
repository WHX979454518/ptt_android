package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import butterknife.Bind
import butterknife.ButterKnife
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.service.provider.ExistingConversationRequest
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import org.apache.commons.lang3.StringUtils
import java.util.*

/**
 * 显示会话列表(Group)的界面
 */
class ConversationListFragment : BaseFragment<Void>() {

    @Bind(R.id.groupList_recyclerView)
    internal lateinit var listView: RecyclerView
    private val adapter = Adapter()
    private lateinit var broker: Broker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        broker = (activity.application as AppComponent).broker
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_group_list, container, false)
        ButterKnife.bind(this, view)

        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        return view
    }

    override fun onDestroyView() {
        ButterKnife.unbind(this)
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()

        broker.getConversationsWithMemberNames(MAX_MEMBER_TO_DISPLAY)
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { adapter.setConversations(it) }
    }

    private inner class Adapter : RecyclerView.Adapter<ConversationItemHolder>() {
        private val conversations = ArrayList<Broker.AggregateInfo<Conversation, String>>()

        fun setConversations(newGroups: Collection<Broker.AggregateInfo<Conversation, String>>?) {
            conversations.clear()
            if (newGroups != null) {
                conversations.addAll(newGroups)
            }
            notifyDataSetChanged()
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationItemHolder {
            return ConversationItemHolder(parent)
        }

        override fun onBindViewHolder(holder: ConversationItemHolder, position: Int) {
            holder.setGroup(conversations[position])
            holder.itemView.setOnClickListener { v ->
                startActivity(RoomActivity.builder(context,
                        ExistingConversationRequest(conversations[position].group.id)))
            }
        }

        override fun getItemCount(): Int {
            return conversations.size
        }
    }

    internal class ConversationItemHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_group_list_item, container, false)) {

        @Bind(R.id.groupListItem_members)
        lateinit var memberView: TextView

        @Bind(R.id.groupListItem_name)
        lateinit var nameView: TextView

        @Bind(R.id.groupListItem_icon)
        lateinit var iconView: ImageView

        init {
            ButterKnife.bind(this, itemView)
        }

        fun setGroup(info: Broker.AggregateInfo<Conversation, String>) {
            nameView.text = info.group.name
            //            Picasso.with(itemView.getContext())
            //                    .load(info.group.getImageUri())
            //                    .fit()
            //                    .into(iconView);

            memberView.text = itemView.resources.getString(if (info.memberCount > info.members.size) R.string.group_member_with_more else R.string.group_member,
                    StringUtils.join(info.members, itemView.resources.getString(R.string.member_separator)))

        }
    }

    companion object {

        private val MAX_MEMBER_TO_DISPLAY = 3
    }
}
