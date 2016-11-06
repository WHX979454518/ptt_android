package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.support.v4.app.Fragment
import android.support.v7.util.SortedList
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.support.v7.widget.util.SortedListAdapterCallback
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import rx.Subscription
import java.io.Serializable

class RoomInvitationListFragment : BottomSheetDialogFragment() {
    private val adapter = Adapter()

    private var subscription: Subscription? = null
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val invitationList = arguments.getSerializable(ARG_INVITATIONS) as List<RoomInvitation>

        subscription = appComponent.userRepository.getUsers(invitationList.map { it.inviterId })
                .getAsync()
                .toObservable()
                .map {
                    val userMap = it.associateBy { it.id }
                    invitationList.map { RoomInvitationData(it, userMap[it.inviterId]) }
                }
                .observeOnMainThread()
                .subscribeSimple {
                    adapter.addInvitations(it)
                    toolbar?.title = R.string.invitation_list.toFormattedString(context, adapter.itemCount)
                }
    }

    override fun onDestroy() {
        subscription?.unsubscribe()
        subscription = null
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_room_invitation_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView: RecyclerView = view.findView(R.id.roomInviteList_recycler)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        toolbar = view.findView(R.id.roomInviteList_toolbar)
        toolbar!!.title = R.string.invitation_list.toFormattedString(context, adapter.itemCount)
        toolbar!!.inflateMenu(R.menu.room_invitation_list)
        toolbar!!.setOnMenuItemClickListener {
            if (it.itemId == R.id.roomInvitationList_ignoreAll) {
                callbacks<Callbacks>()?.ignoreAllInvitations(this)
                true
            } else {
                false
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private val invitationList = SortedList<RoomInvitationData>(RoomInvitationData::class.java, SortedList.BatchedCallback(object : SortedListAdapterCallback<RoomInvitationData>(this) {
            override fun areContentsTheSame(oldItem: RoomInvitationData, newItem: RoomInvitationData): Boolean {
                return areItemsTheSame(oldItem, newItem)
            }

            override fun areItemsTheSame(item1: RoomInvitationData, item2: RoomInvitationData): Boolean {
                return item1 == item2
            }

            override fun compare(o1: RoomInvitationData, o2: RoomInvitationData): Int {
                return o2.invitation.inviteTime.compareTo(o1.invitation.inviteTime)
            }
        }))

        fun addInvitations(newList: List<RoomInvitationData>) {
            invitationList.beginBatchedUpdates()
            invitationList.addAll(newList)
            invitationList.endBatchedUpdates()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_invitation_item, parent, false)).apply {
                joinButton.setOnClickListener {
                    (activity as? BaseActivity)?.joinRoom(invitationList[adapterPosition].invitation.room.id, true)
                    dismissImmediately()
                }

                val onClickListener: (View) -> Unit = {
                    activity.startActivityWithAnimation(UserDetailsActivity.build(it.context, invitationList[adapterPosition].invitation.inviterId))
                }
                inviterIconView.setOnClickListener(onClickListener)
                inviterNameView.setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount(): Int {
            return invitationList.size()
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val invitation = invitationList[position]
            holder.inviterIconView.setImageDrawable(invitation.inviter?.createDrawable(holder.itemView.context))
            holder.inviterNameView.text = invitation.inviter?.name
            holder.inviteTimeView.text = DateUtils.getRelativeTimeSpanString(invitation.invitation.inviteTime.time)
        }
    }

    private class ViewHolder(rootView: View,
                             val inviterIconView: ImageView = rootView.findView(R.id.userItem_avatar),
                             val inviterNameView: TextView = rootView.findView(R.id.userItem_name),
                             val inviteTimeView: TextView = rootView.findView(R.id.inviteItem_time),
                             val joinButton: View = rootView.findView(R.id.inviteItem_join)
    ) : RecyclerView.ViewHolder(rootView)

    private data class RoomInvitationData(val invitation: RoomInvitation,
                                          val inviter: User?)

    interface Callbacks {
        fun ignoreAllInvitations(from: Fragment?)
    }

    companion object {
        const val ARG_INVITATIONS = "arg_in"

        fun build(invitations: List<RoomInvitation>): RoomInvitationListFragment {
            return RoomInvitationListFragment().apply {
                arguments = Bundle(1).apply {
                    putSerializable(ARG_INVITATIONS, invitations as Serializable)
                }
            }
        }
    }
}