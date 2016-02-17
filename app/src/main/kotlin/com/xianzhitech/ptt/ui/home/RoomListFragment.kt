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
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.repo.RoomWithMemberNames
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.joinRoom

/**
 * 显示会话列表(Room)的界面
 */
class RoomListFragment : BaseFragment<RoomListFragment.Callbacks>() {

    private var listView: RecyclerView? = null
    private var errorView : View? = null
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        (context.applicationContext as AppComponent).roomRepository.getRoomsWithMemberNames(3)
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    adapter.rooms = it
                    errorView?.setVisible(it.isEmpty())
                }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room_list, container, false)?.apply {
            errorView = findViewById(R.id.roomList_emptyView).apply {
                setOnClickListener { callbacks?.navigateToContactList() }
            }
            listView = findView<RecyclerView>(R.id.roomList_recyclerView).apply {
                this.layoutManager = LinearLayoutManager(context)
                this.adapter = this@RoomListFragment.adapter
            }
        }
    }

    override fun onDestroyView() {
        listView = null
        errorView = null
        super.onDestroyView()
    }

    private inner class Adapter : RecyclerView.Adapter<RoomItemHolder>() {
        var rooms: List<RoomWithMemberNames> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomItemHolder {
            return RoomItemHolder(parent)
        }

        override fun onBindViewHolder(holder: RoomItemHolder, position: Int) {
            holder.setRoom(rooms[position])
            holder.itemView.setOnClickListener { v ->
                context.joinRoom(rooms[position].room.id, null, false, bindToLifecycle())
            }
        }

        override fun getItemCount(): Int {
            return rooms.size
        }
    }

    private class RoomItemHolder(container: ViewGroup,
                                 rootView : View = container.inflate(R.layout.view_group_list_item),
                                 private val memberView: TextView = rootView.findView(R.id.groupListItem_members),
                                 private val nameView: TextView = rootView.findView(R.id.groupListItem_name),
                                 private val iconView: ImageView = rootView.findView(R.id.groupListItem_icon))
    : RecyclerView.ViewHolder(rootView) {

        fun setRoom(room: RoomWithMemberNames) {
            val memberText = room.getMemberNames(itemView.context)

            if (room.room.name.isNullOrBlank()) {
                nameView.text = memberText
                memberView.visibility = View.GONE
            } else {
                nameView.text = room.room.name
                //            Picasso.with(itemView.getContext())
                //                    .load(info.group.getImageUri())
                //                    .fit()
                //                    .into(iconView);

                memberView.text = memberText
                memberView.visibility = View.VISIBLE
            }

        }
    }

    interface Callbacks {
        fun navigateToContactList()
    }
}
