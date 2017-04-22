package com.xianzhitech.ptt.ui.group

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
import com.xianzhitech.ptt.viewmodel.GroupMemberListViewModel


class GroupMemberListFragment : ModelListFragment<GroupMemberListViewModel, ViewModelListBinding>() {
    override fun onCreateViewModel(): GroupMemberListViewModel {
        return GroupMemberListViewModel(appComponent, arguments.getString(ARG_GROUP_ID))
    }

    override fun onStart() {
        super.onStart()

        if (activity is FragmentDisplayActivity) {
            activity.title = getString(R.string.group_members)
        }
    }

    override val recyclerView: RecyclerView = dataBinding.recyclerView
    override val sideNavigationView: SideNavigationView = dataBinding.sideBar
    override val currentCharView: TextView = dataBinding.currentChar

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): ViewModelListBinding {
        return ViewModelListBinding.inflate(inflater, container, false)
    }

    companion object {
        const val ARG_GROUP_ID = "group_id"
    }
}