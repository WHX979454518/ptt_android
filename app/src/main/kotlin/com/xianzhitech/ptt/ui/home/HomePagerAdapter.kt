package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment


class HomePagerAdapter(private val context: Context,
                       fm : FragmentManager) : FragmentStatePagerAdapter(fm) {
    val fragments = listOf(
            R.id.home_rooms to RoomListFragment::class.java,
            R.id.home_contact to ContactsFragment::class.java,
            R.id.home_profile to ProfileFragment::class.java
    )

    override fun getItem(position: Int): Fragment {
        return Fragment.instantiate(context, fragments[position].second.name)
    }

    override fun getCount(): Int {
        return fragments.size
    }
}