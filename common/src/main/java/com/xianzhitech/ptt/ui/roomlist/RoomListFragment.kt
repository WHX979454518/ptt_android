package com.xianzhitech.ptt.ui.roomlist

import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.databinding.FragmentRoomListBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.chat.ChatFragment
import com.xianzhitech.ptt.viewmodel.RoomListViewModel


class RoomListFragment : BaseViewModelFragment<RoomListViewModel, FragmentRoomListBinding>(), RoomListViewModel.Navigator {
    private val adapter = RoomListAdapter()

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentRoomListBinding {
        return FragmentRoomListBinding.inflate(inflater, container, false).apply {
            roomListRecyclerView.layoutManager = LinearLayoutManager(context)
            roomListRecyclerView.adapter = adapter
        }
    }

    override fun onCreateViewModel(): RoomListViewModel {
        return RoomListViewModel(appComponent, this).apply {
            roomViewModels.addOnListChangedCallback(adapter.listChangeListener)
        }
    }

    interface Callbacks {
        fun navigateToChatRoomPage(room: Room)
    }

    override fun navigateToRoom(room: Room) {
        callbacks<Callbacks>()?.navigateToChatRoomPage(room)
    }
}