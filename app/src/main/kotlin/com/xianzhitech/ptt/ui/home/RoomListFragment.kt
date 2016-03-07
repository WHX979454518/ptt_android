package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.util.SortedList
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.repo.RoomWithMembers
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.joinRoom
import com.xianzhitech.ptt.ui.widget.MultiDrawable
import rx.Observable
import java.text.Collator
import java.util.*

/**
 * 显示会话列表(Room)的界面
 */
class RoomListFragment : BaseFragment<RoomListFragment.Callbacks>() {

    private var listView: RecyclerView? = null
    private var errorView : View? = null
    private val adapter = Adapter()
    private val adapterCallback = object : SortedListAdapterCallback<RoomWithMembers>(adapter) {
        private val collator = Collator.getInstance()

        override fun areItemsTheSame(p0: RoomWithMembers, p1: RoomWithMembers) = p0.room.id == p1.room.id

        override fun compare(p0: RoomWithMembers, p1: RoomWithMembers): Int {
            if (areItemsTheSame(p0, p1)) {
                return 0
            }

            var rc : Int = p0.room.lastActiveTime.timeOrZero().compareTo(p1.room.lastActiveTime.timeOrZero())
            if (rc != 0) {
                return rc
            }

            rc = collator.compare(p0.getRoomName(context), p1.getRoomName(context))
            if (rc != 0) {
                return rc
            }

            return p0.room.id.compareTo(p1.room.id)
        }

        override fun areContentsTheSame(p0: RoomWithMembers, p1: RoomWithMembers) = areItemsTheSame(p0, p1)

        private fun Date?.timeOrZero() = this?.time ?: 0
    }
    private val roomList = SortedList<RoomWithMembers>(RoomWithMembers::class.java, SortedList.BatchedCallback(adapterCallback))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent
        Observable.combineLatest(
                appComponent.roomRepository.getRoomsWithMembers(9),
                appComponent.connectToBackgroundService().flatMap { it.loginState }.first { it.currentUserID != null },
                { first, second -> first to second })
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    adapter.currentUserId = it.second.currentUserID!!
                    roomList.beginBatchedUpdates()
                    roomList.clear()
                    roomList.addAll(it.first)
                    roomList.endBatchedUpdates()
                    errorView?.setVisible(it.first.isEmpty())
                }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room_list, container, false)?.apply {
            errorView = findViewById(R.id.roomList_emptyView).apply {
                setOnClickListener { callbacks?.navigateToContactList() }
            }
            listView = findView<RecyclerView>(R.id.roomList_recyclerView).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = this@RoomListFragment.adapter
            }
        }
    }

    override fun onDestroyView() {
        listView = null
        errorView = null
        super.onDestroyView()
    }



    private inner class Adapter : RecyclerView.Adapter<RoomItemHolder>() {
        lateinit var currentUserId: String

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomItemHolder {
            return RoomItemHolder(parent)
        }

        override fun onBindViewHolder(holder: RoomItemHolder, position: Int) {
            holder.setRoom(roomList[position], currentUserId)
            holder.itemView.setOnClickListener { v ->
                context.joinRoom(roomList[position].room.id, null, false, bindToLifecycle())
            }
        }

        override fun getItemCount(): Int {
            return roomList.size()
        }
    }

    private inner class RoomItemHolder(container: ViewGroup,
                                 rootView : View = container.inflate(R.layout.view_group_list_item),
                                 private val memberView: TextView = rootView.findView(R.id.groupListItem_members),
                                 private val nameView: TextView = rootView.findView(R.id.groupListItem_name),
                                 private val iconView: ImageView = rootView.findView(R.id.groupListItem_icon))
    : RecyclerView.ViewHolder(rootView) {

        fun setRoom(room: RoomWithMembers, currentUserId: String) {
            val memberText = room.getMemberNames(itemView.context)
            val nonSelfUser = room.members.first { it.id != currentUserId }

            if (room.room.name.isNullOrBlank()) {
                nameView.text = if (room.memberCount == 2) nonSelfUser.name else memberText
                memberView.visibility = View.GONE
            } else {
                nameView.text = room.room.name
                memberView.text = memberText
                memberView.visibility = View.VISIBLE
            }

            if (room.memberCount > 2) {
                val roomDrawable = if (iconView.drawable is MultiDrawable) iconView.drawable as MultiDrawable
                else MultiDrawable(itemView.context).apply { iconView.setImageDrawable(this) }
                roomDrawable.children = room.members.map { it.createAvatarDrawable(this@RoomListFragment) }
            }
            else {
                iconView.setImageDrawable(nonSelfUser.createAvatarDrawable(this@RoomListFragment))
            }

        }
    }

    interface Callbacks {
        fun navigateToContactList()
    }
}
