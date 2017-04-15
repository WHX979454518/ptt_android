package com.xianzhitech.ptt.ui.home

import android.annotation.SuppressLint
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.ActivityHomeBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.ui.base.BaseViewModelActivity
import com.xianzhitech.ptt.viewmodel.HomeViewModel


class HomeActivity : BaseViewModelActivity<HomeViewModel, ActivityHomeBinding>(), HomeViewModel.Navigator {
    override fun navigateToWalkieTalkiePage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun navigateToVideoChatPage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreateViewModel(): HomeViewModel {
        return HomeViewModel(appComponent, applicationContext, this)
    }

    @SuppressLint("CommitTransaction")
    override fun onStart() {
        super.onStart()

        viewModel.currentTab.toRxObservable()
                .subscribe { id ->
                    val newFragClass = when (id) {
                        R.id.home_rooms -> RoomListFragment::class.java
                        R.id.home_contact -> ContactsFragment::class.java
                        R.id.home_profile -> ProfileFragment::class.java
                        else -> throw IllegalStateException()
                    }

                    if (newFragClass.isInstance(supportFragmentManager.findFragmentById(binding.content.id)).not()) {
                        supportFragmentManager.beginTransaction()
                                .replace(binding.content.id, Fragment.instantiate(this, newFragClass.name))
                                .commitNow()
                    }
                }
    }

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): ActivityHomeBinding {
        return ActivityHomeBinding.inflate(layoutInflater).apply {
            tabBar.setOnNavigationItemSelectedListener {
                viewModel.onClickTab(it.itemId)
                true
            }
        }
    }
}