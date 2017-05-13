package com.xianzhitech.ptt.ui.walkie

import android.databinding.ObservableList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.databinding.FragmentWalkieRoomBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.room.RoomInvitationFragment
import com.xianzhitech.ptt.ui.room.RoomInvitationListFragment
import com.xianzhitech.ptt.ui.user.UserItemHolder
import com.xianzhitech.ptt.ui.user.UserListAdapter
import com.xianzhitech.ptt.viewmodel.WalkieRoomViewModel


class WalkieRoomFragment : BaseViewModelFragment<WalkieRoomViewModel, FragmentWalkieRoomBinding>(),
        WalkieRoomViewModel.Navigator,
        RoomInvitationListFragment.Callbacks,
        BackPressable,
        RoomInvitationFragment.Callbacks {

    private val onlineUserAdapter = object : UserListAdapter(R.layout.view_room_online_member_list_item) {
        override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.itemView.findViewById(R.id.userItem_initiatorLabel)?.setVisible(holder.userId == appComponent.signalBroker.currentWalkieRoomState.value.currentRoomInitiatorUserId)
        }
    }
    private var popupWindow: PopupWindow? = null

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentWalkieRoomBinding {
        return FragmentWalkieRoomBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel(): WalkieRoomViewModel {
        return WalkieRoomViewModel(
                appComponent = appComponent,
                requestJoinRoomId = arguments.getString(ARG_REQUEST_JOIN_ROOM_ID),
                requestJoinRoomFromInvitation = arguments.getBoolean(ARG_REQUEST_JOIN_ROOM_FROM_INVITATION),
                pendingInvitations = arguments.getParcelableArrayList<WalkieRoomInvitationEvent>(ARG_PENDING_INVITATIONS) ?: emptyList(),
                navigator = this).apply {
            onlineMembers.addOnListChangedCallback(object : ObservableList.OnListChangedCallback<ObservableList<User>>() {
                override fun onItemRangeChanged(p0: ObservableList<User>, p1: Int, p2: Int) {
                    onlineUserAdapter.setUsers(p0)
                }

                override fun onItemRangeRemoved(p0: ObservableList<User>, p1: Int, p2: Int) {
                    onlineUserAdapter.setUsers(p0)
                }

                override fun onItemRangeMoved(p0: ObservableList<User>, p1: Int, p2: Int, p3: Int) {
                    onlineUserAdapter.setUsers(p0)
                }

                override fun onItemRangeInserted(p0: ObservableList<User>, p1: Int, p2: Int) {
                    onlineUserAdapter.setUsers(p0)
                }

                override fun onChanged(p0: ObservableList<User>) {
                    onlineUserAdapter.setUsers(p0)
                }
            })
        }
    }

    override fun showOnlinePopupWindow() {
        if (popupWindow != null && popupWindow!!.isShowing) {
            popupWindow?.dismiss()
        } else {
            ensurePopupWindow().showAsDropDown(dataBinding.roomTitle)
        }
    }

    private fun ensurePopupWindow(): PopupWindow {
        if (popupWindow == null) {
            popupWindow = PopupWindow(onCreatePopupWindowView(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            popupWindow!!.isOutsideTouchable = true
            popupWindow!!.isFocusable = true
            popupWindow!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return popupWindow!!
    }

    override fun onBackPressed(): Boolean {
        if (popupWindow != null && popupWindow!!.isShowing) {
            popupWindow!!.dismiss()
            return true
        }

        return false
    }

    override fun onStop() {
        super.onStop()

        popupWindow?.dismiss()
    }

    private fun onCreatePopupWindowView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.view_room_online_info, null)
        view.findView<RecyclerView>(R.id.roomOnlineInfo_list).apply {
            layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.horizontal_member_item_count))
            adapter = onlineUserAdapter
        }
        view.findViewById(R.id.roomOnlineInfo_all)!!.setOnClickListener {
            appComponent.signalBroker.peekWalkieRoomId()?.let { callbacks<Callbacks>()?.navigateToRoomMemberPage(it) }
        }
        return view
    }


    override fun navigateToRoomNoLongerExistsPage() {
        Toast.makeText(context, R.string.error_room_not_exists, Toast.LENGTH_LONG).show()
        closeRoomPage()
    }

    override fun navigateToErrorPage(throwable: Throwable) {
        throwable.toast()
    }

    override fun closeRoomPage() {
        callbacks<Callbacks>()!!.closeRoomPage()
    }

    override fun navigateToUserDetailsPage(userId: String) {
        callbacks<Callbacks>()!!.navigateToUserDetailsPage(userId)
    }

    override fun navigateToRoomMemberPage(roomId: String) {
        callbacks<Callbacks>()!!.navigateToRoomMemberPage(roomId)
    }

    override fun displayNoPermissionError() {
        Toast.makeText(context, R.string.error_no_permission, Toast.LENGTH_LONG).show()
    }

    override fun dismissInvitations() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showInvitationList(invitations: List<WalkieRoomInvitationEvent>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun ignoreAllInvitations(from: Fragment?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun onNewPendingInvitation(pendingInvitations: List<WalkieRoomInvitationEvent>) {

    }

    fun joinRoom(roomId: String, fromInvitation: Boolean) {
        viewModel.joinRoom(roomId, fromInvitation)
    }

    interface Callbacks {
        fun navigateToRoomMemberPage(roomId: String)
        fun navigateToUserDetailsPage(userId: String)
        fun closeRoomPage()
    }

    companion object {
        const val ARG_REQUEST_JOIN_ROOM_ID = "join_room_id"
        const val ARG_REQUEST_JOIN_ROOM_FROM_INVITATION = "join_room_from_invitation"
        const val ARG_PENDING_INVITATIONS = "pending_invitation"
    }
}