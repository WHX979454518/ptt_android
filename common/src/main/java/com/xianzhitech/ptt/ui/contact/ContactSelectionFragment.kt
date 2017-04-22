package com.xianzhitech.ptt.ui.contact

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
import com.xianzhitech.ptt.viewmodel.ContactSelectionListViewModel


class ContactSelectionFragment : ModelListFragment<ContactSelectionListViewModel, ViewModelListBinding>() {
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

        (activity as? FragmentDisplayActivity)?.setTitle(R.string.select_contact)
    }

    override fun onCreateViewModel(): ContactSelectionListViewModel {
        return ContactSelectionListViewModel(
                preselectedIds = arguments?.getStringArrayList(ARG_PRESELECTED_MODEL_IDS) ?: emptyList(),
                appComponent = appComponent,
                showGroup = arguments?.getBoolean(SHOW_GROUP, false) ?: false)
    }


    companion object {
        const val ARG_PRESELECTED_MODEL_IDS = "model_ids"
        const val SHOW_GROUP = "show_group"
    }
}