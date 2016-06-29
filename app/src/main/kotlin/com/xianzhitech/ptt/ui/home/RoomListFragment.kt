package com.xianzhitech.ptt.ui.home

import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomModel
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomDetailsActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.RoomComparator
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * 显示会话列表(Room)的界面
 */
class RoomListFragment : BaseFragment() {

    private var listView: RecyclerView? = null
    private var errorView: View? = null
    private val adapter = Adapter()

    private val roomList = arrayListOf<RoomModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.room_list, menu)
        val menuItem = menu.findItem(R.id.roomList_menuNew)
        menuItem.icon = context.getTintedDrawable(R.drawable.ic_group_add_24dp, Color.WHITE)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.roomList_menuNew) {
            callbacks<Callbacks>()?.requestCreateNewRoom()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent
        Observable.combineLatest(
                appComponent.roomRepository.getAllRooms().observe(),
                appComponent.signalHandler.loginState.first { it.currentUserID != null },
                { first, second -> first to second })
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    adapter.currentUserId = it.second.currentUserID!!
                    roomList.clear()
                    roomList.addAll(it.first)
                    roomList.sortWith(RoomComparator)
                    adapter.notifyDataSetChanged()
                    errorView?.setVisible(it.first.isEmpty())
                }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room_list, container, false)?.apply {
            errorView = findViewById(R.id.roomList_emptyView).apply {
                setOnClickListener { callbacks<Callbacks>()?.navigateToContactList() }
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
            val holder = RoomItemHolder(parent)
            holder.iconView.setOnClickListener {
                holder.room?.let { room ->
                    val intent = if (room.associatedGroupIds.isEmpty() && room.extraMemberIds.size <= 2) {
                        UserDetailsActivity.build(activity, room.extraMemberIds.first { it != currentUserId })
                    } else {
                        RoomDetailsActivity.build(activity, room.id)
                    }
                    activity.startActivityWithAnimation(intent)
                }
            }

            holder.itemView.setOnLongClickListener {
                holder.room?.let { room ->
                    onLongClickOnRoom(holder.itemView, room)
                } ?: false
            }
            return holder
        }

        override fun onBindViewHolder(holder: RoomItemHolder, position: Int) {
            holder.setRoom(roomList[position], currentUserId)
            holder.itemView.setOnClickListener { v ->
                (activity as BaseActivity).joinRoom(roomList[position].id)
            }
        }

        override fun onViewDetachedFromWindow(holder: RoomItemHolder) {
            super.onViewDetachedFromWindow(holder)
            holder.clear()
        }

        override fun getItemCount(): Int {
            return roomList.size
        }
    }

    private fun onLongClickOnRoom(anchorView: View, room: Room): Boolean {
        PopupMenu(context, anchorView).apply {
            inflate(R.menu.room_operation)
            setOnMenuItemClickListener {
                if (room.id == appComponent.signalHandler.currentRoomId) {
                    Toast.makeText(context, R.string.error_delete_fail_in_room, Toast.LENGTH_LONG).show()
                    return@setOnMenuItemClickListener true
                }

                val oldPosition = roomList.indexOfFirst { it.id == room.id }
                roomList.removeAt(oldPosition)
                adapter.notifyItemRemoved(oldPosition)
                appComponent.roomRepository.removeRooms(listOf(room.id)).execAsync(notifyChanges = false).subscribeSimple()
                true
            }
        }.show()

        return true
    }

    private class RoomItemHolder(container: ViewGroup,
                                 rootView: View = container.inflate(R.layout.view_room_item),
                                 val secondaryView: TextView = rootView.findView(R.id.roomItem_secondaryTitle),
                                 val primaryView: TextView = rootView.findView(R.id.roomItem_primaryTitle),
                                 val iconView: ImageView = rootView.findView(R.id.roomItem_icon)) : RecyclerView.ViewHolder(rootView) {

        private var subscription: Subscription? = null
        var room: Room? = null

        fun setRoom(room: RoomModel, currentUserId: String) {
            if (this.room?.id == room.id) {
                return
            }

            this.room = room
            this.subscription?.unsubscribe()
            val appComponent = itemView.context.applicationContext as AppComponent
            this.subscription = appComponent.roomRepository.getRoomName(room.id, excludeUserIds = arrayOf(currentUserId)).observe()
                    .combineWith(appComponent.userRepository.getUser(room.lastSpeakMemberId).observe())
                    .switchMap { result ->
                        (Observable.interval(0, 1, TimeUnit.MINUTES, AndroidSchedulers.mainThread()) as Observable<*>)
                                .mergeWith(appComponent.signalHandler.roomState.distinctUntilChanged { it.currentRoomId } as Observable<out Nothing>)
                                .map { result }
                    }
                    .observeOnMainThread()
                    .subscribeSimple {
                        val (roomName: RoomName?, lastActiveUser: User?) = it
                        val lastActiveTime = room.lastSpeakTime
                        val currentRoomId = appComponent.signalHandler.peekRoomState().currentRoomId
                        if (currentRoomId == room.id) {
                            val postfix = R.string.in_room_postfix.toFormattedString(itemView.context)
                            val fullRoomName = roomName?.name + postfix
                            primaryView.text = SpannableStringBuilder(fullRoomName).apply {
                                setSpan(ForegroundColorSpan(itemView.context.getColorCompat(R.color.red)),
                                        0, fullRoomName.length, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
                            }

                        } else {
                            primaryView.text = roomName?.name
                        }

                        secondaryView.setVisible(lastActiveUser != null && lastActiveTime != null)
                        if (lastActiveUser != null && lastActiveTime != null) {
                            secondaryView.text = R.string.room_last_active_user_time.toFormattedString(itemView.context,
                                    lastActiveUser.name, DateUtils.getRelativeTimeSpanString(lastActiveTime.time))
                            val drawableId = if (lastActiveUser.id == currentUserId) R.drawable.ic_call_made_black else R.drawable.ic_call_received_black
                            val drawable = itemView.context.getTintedDrawable(drawableId, secondaryView.textColors.defaultColor)
                            drawable.setBounds(0, 0, secondaryView.textSize.toInt(), secondaryView.textSize.toInt())
                            secondaryView.setCompoundDrawables(drawable, null, null, null)
                        }
                    }

            iconView.setImageDrawable(room.createDrawable(itemView.context))
        }

        fun clear() {
            room = null
            subscription?.unsubscribe()
            subscription = null
        }
    }

    interface Callbacks {
        fun navigateToContactList()
        fun requestCreateNewRoom()
    }
}
