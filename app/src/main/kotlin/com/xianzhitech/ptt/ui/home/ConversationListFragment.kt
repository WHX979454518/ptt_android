package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getMemberNames
import com.xianzhitech.ptt.presenter.ConversationListPresenter
import com.xianzhitech.ptt.presenter.ConversationListPresenterView
import com.xianzhitech.ptt.repo.ConversationWithMemberNames
import com.xianzhitech.ptt.service.provider.ConversationFromExisting
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import kotlin.collections.emptyList
import kotlin.text.isNullOrBlank

/**
 * 显示会话列表(Group)的界面
 */
class ConversationListFragment : BaseFragment<Void>(), ConversationListPresenterView {

    private var listView: RecyclerView? = null
    private val adapter = Adapter()
    private lateinit var presenter: ConversationListPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = ConversationListPresenter((activity.application as AppComponent).conversationRepository)
    }

    override fun onStart() {
        super.onStart()

        presenter.attachView(this)
    }

    override fun showLoading(visible: Boolean) {
        // Nothing to show
    }

    override fun showConversationList(result: List<ConversationWithMemberNames>) {
        adapter.conversations = result
    }


    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_conversation_list, container, false)?.apply {
            listView = findView<RecyclerView>(R.id.conversationList_recyclerView).apply {
                this.layoutManager = LinearLayoutManager(context)
                this.adapter = this@ConversationListFragment.adapter
            }
        }
    }

    override fun onDestroyView() {
        listView = null
        super.onDestroyView()
    }

    private inner class Adapter : RecyclerView.Adapter<ConversationItemHolder>() {
        var conversations: List<ConversationWithMemberNames> = emptyList()
            set(value) {
                field = value
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationItemHolder {
            return ConversationItemHolder(parent)
        }

        override fun onBindViewHolder(holder: ConversationItemHolder, position: Int) {
            holder.setConversation(conversations[position])
            holder.itemView.setOnClickListener { v ->
                startActivity(RoomActivity.builder(context,
                        ConversationFromExisting(conversations[position].conversation.id)))
            }
        }

        override fun getItemCount(): Int {
            return conversations.size
        }
    }

    internal class ConversationItemHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_group_list_item, container, false)) {

        var memberView: TextView
        var nameView: TextView
        var iconView: ImageView

        init {
            memberView = itemView.findView(R.id.groupListItem_members)
            nameView = itemView.findView(R.id.groupListItem_name)
            iconView = itemView.findView(R.id.groupListItem_icon)
        }

        fun setConversation(info: ConversationWithMemberNames) {
            val memberText = info.getMemberNames(itemView.context)

            if (info.conversation.name.isNullOrBlank()) {
                nameView.text = memberText
                memberView.visibility = View.GONE
            } else {
                nameView.text = info.conversation.name
                //            Picasso.with(itemView.getContext())
                //                    .load(info.group.getImageUri())
                //                    .fit()
                //                    .into(iconView);

                memberView.text = memberText
                memberView.visibility = View.VISIBLE
            }

        }
    }
}
