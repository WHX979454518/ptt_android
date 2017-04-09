package com.xianzhitech.ptt.ui.roomlist

import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentRoomListBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment


class RoomListFragment : BaseViewModelFragment<RoomListViewModel, FragmentRoomListBinding>() {
    private val adapter = RoomListAdapter()

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentRoomListBinding {
        return FragmentRoomListBinding.inflate(inflater, container, false).apply {
            roomListRecyclerView.layoutManager = LinearLayoutManager(context)
            roomListRecyclerView.adapter = adapter
        }
    }

    override fun onCreateViewModel(): RoomListViewModel {
        return RoomListViewModel(appComponent).apply {
            roomViewModels.addOnListChangedCallback(adapter.listChangeListener)
        }
    }
}