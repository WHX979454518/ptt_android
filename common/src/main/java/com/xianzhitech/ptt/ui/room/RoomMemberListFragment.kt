package com.xianzhitech.ptt.ui.room

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.ViewModelListBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.modellist.ModelListFragment
import com.xianzhitech.ptt.ui.widget.SideNavigationView
import com.xianzhitech.ptt.viewmodel.RoomMemberListViewModel


class RoomMemberListFragment : ModelListFragment<RoomMemberListViewModel, ViewModelListBinding>() {
    override val recyclerView: RecyclerView
        get() = dataBinding.recyclerView
    override val sideNavigationView: SideNavigationView
        get() = dataBinding.sideBar
    override val currentCharView: TextView
        get() = dataBinding.currentChar

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): ViewModelListBinding {
        return ViewModelListBinding.inflate(inflater, container, false)
    }

    override fun onStart() {
        super.onStart()

        (activity as? FragmentDisplayActivity)?.title = getString(R.string.room_members)
    }

    override fun onCreateViewModel(): RoomMemberListViewModel {
        return RoomMemberListViewModel(appComponent, arguments.getString(ARG_ROOM_ID))
    }

    companion object {
        const val ARG_ROOM_ID = "room_id"
    }
}