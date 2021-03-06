package com.xianzhitech.ptt.ui.home

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.databinding.FragmentContactsBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.chat.ChatFragment
import com.xianzhitech.ptt.ui.modellist.ModelListAdapter
import com.xianzhitech.ptt.ui.modellist.ModelListFragment
import com.xianzhitech.ptt.ui.widget.SideNavigationView

class ContactsFragment : ModelListFragment<ContactsViewModel, FragmentContactsBinding>(), ContactsViewModel.Navigator {


    override fun onCreateViewModel(): ContactsViewModel {
        return ContactsViewModel(appComponent, this)
    }

    override fun onCreateModelListAdapter(): ModelListAdapter {
        return ModelListAdapter(this, R.layout.view_contact_item)
    }

    override val recyclerView: RecyclerView
        get() = dataBinding.modelList!!.recyclerView

    override val sideNavigationView: SideNavigationView
        get() = dataBinding.modelList!!.sideBar

    override val currentCharView: TextView
        get() = dataBinding.modelList!!.currentChar


    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentContactsBinding {
        return FragmentContactsBinding.inflate(inflater, container, false)
    }

    override fun displayContactSyncSuccess() {
        Toast.makeText(BaseApp.instance, R.string.contact_updated, Toast.LENGTH_LONG).show()
    }

    override fun displayContactSyncError(err: Throwable) {
        err.toast()
    }

    override fun navigateToChatRoom(room: Room) {
        startActivity(FragmentDisplayActivity.createIntent(ChatFragment::class.java, ChatFragment.ARG_ROOM_ID, room.id))
    }

    override fun displayCreateRoomError(err: Throwable) {
        err.toast()
    }
}
