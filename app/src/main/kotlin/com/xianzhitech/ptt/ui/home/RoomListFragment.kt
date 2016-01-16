package com.xianzhitech.ptt.ui.home

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.repo.RoomWithMemberNames
import com.xianzhitech.ptt.service.provider.JoinRoomFromExisting
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import java.util.concurrent.TimeUnit
import kotlin.collections.emptyList
import kotlin.text.isNullOrBlank

/**
 * 显示会话列表(Room)的界面
 */
class RoomListFragment : BaseFragment<Void>() {

    private var listView: RecyclerView? = null
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        (context.applicationContext as AppComponent).roomRepository.getRoomsWithMemberNames(3)
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { adapter.rooms = it }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_conversation_list, container, false)?.apply {
            listView = findView<RecyclerView>(R.id.conversationList_recyclerView).apply {
                this.layoutManager = LinearLayoutManager(context)
                this.adapter = this@RoomListFragment.adapter
            }
        }
    }

    override fun onDestroyView() {
        listView = null
        super.onDestroyView()
    }

    private inner class Adapter : RecyclerView.Adapter<ConversationItemHolder>() {
        var rooms: List<RoomWithMemberNames> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationItemHolder {
            return ConversationItemHolder(parent)
        }

        override fun onBindViewHolder(holder: ConversationItemHolder, position: Int) {
            holder.setConversation(rooms[position])
            holder.itemView.setOnClickListener { v ->
                val request = JoinRoomFromExisting(rooms[position].room.id)
                val dialog = ProgressDialog.show(context, R.string.please_wait.toFormattedString(context),
                        R.string.joining_room.toFormattedString(context), true ,false)

                context.ensureConnectivity()
                        .flatMap { (context.applicationContext as AppComponent).connectToBackgroundService() }
                        .flatMap { binder ->
                            binder.requestJoinRoom(request)
                                    .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                    .doOnError { binder.requestQuitCurrentRoom() }
                        }
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<Unit>(context) {
                            override fun onError(e: Throwable) {
                                super.onError(e)
                                dialog.dismiss()
                            }

                            override fun onNext(t: Unit) {
                                dialog.dismiss()
                                startActivity(Intent(context, RoomActivity::class.java))
                            }
                        })
            }
        }

        override fun getItemCount(): Int {
            return rooms.size
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

        fun setConversation(info: RoomWithMemberNames) {
            val memberText = info.getMemberNames(itemView.context)

            if (info.room.name.isNullOrBlank()) {
                nameView.text = memberText
                memberView.visibility = View.GONE
            } else {
                nameView.text = info.room.name
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
