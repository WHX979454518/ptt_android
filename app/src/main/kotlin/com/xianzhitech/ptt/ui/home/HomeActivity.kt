package com.xianzhitech.ptt.ui.home

import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import com.xianzhitech.ptt.databinding.ActivityHomeBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.ui.base.BaseViewModelActivity
import com.xianzhitech.ptt.viewmodel.HomeViewModel


class HomeActivity : BaseViewModelActivity<HomeViewModel, ActivityHomeBinding>(), HomeViewModel.Navigator {
    private lateinit var adapter : HomePagerAdapter

    override fun navigateToWalkieTalkiePage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun navigateToVideoChatPage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

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
}