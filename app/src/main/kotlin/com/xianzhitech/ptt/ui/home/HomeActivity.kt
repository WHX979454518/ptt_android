package com.xianzhitech.ptt.ui.home

import android.content.Intent
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.databinding.ActivityHomeBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.ui.base.BaseViewModelActivity
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.chat.ChatFragment
import com.xianzhitech.ptt.ui.login.LoginActivity
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment
import com.xianzhitech.ptt.viewmodel.HomeViewModel


class HomeActivity : BaseViewModelActivity<HomeViewModel, ActivityHomeBinding>(), HomeViewModel.Navigator, RoomListFragment.Callbacks, ProfileFragment.Callbacks {
    private lateinit var adapter : HomePagerAdapter

    override fun onCreateViewModel(): HomeViewModel {
        return HomeViewModel(appComponent, applicationContext, this)
    }

    override fun onStart() {
        super.onStart()

        viewModel.currentTab.toRxObservable()
                .subscribe { id ->
                    val index = adapter.fragments.indexOfFirst { it.first == id.get() }
                    if (index != binding.viewPager.currentItem) {
                        binding.viewPager.setCurrentItem(index, true)
                    }

                    val item = binding.tabBar.menu.findItem(id.get())
                    if (item.isChecked.not()) {
                        item.isChecked = true
                    }
                }

    }

    override fun onLoggedOut() {
        startActivity(Intent(this, LoginActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): ActivityHomeBinding {
        adapter = HomePagerAdapter(this, supportFragmentManager)

        return ActivityHomeBinding.inflate(layoutInflater).apply {

            viewPager.adapter = adapter
            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) { }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    viewModel.currentTab.set(adapter.fragments[position].first)
                }
            })

            tabBar.setOnNavigationItemSelectedListener {
                viewModel.currentTab.set(it.itemId)
                true
            }
        }
    }

    override fun navigateToWalkieTalkiePage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun navigateToVideoChatPage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun navigateToChatRoomPage(room: Room) {
        startActivityWithAnimation(
                FragmentDisplayActivity.createIntent(ChatFragment::class.java, ChatFragment.ARG_ROOM_ID, room.id)
        )
    }
}