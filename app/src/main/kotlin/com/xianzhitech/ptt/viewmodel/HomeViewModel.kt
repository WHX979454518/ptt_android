package com.xianzhitech.ptt.viewmodel

import android.content.Context
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R


class HomeViewModel(appComponent: AppComponent,
                    appContext: Context,
                    navigator: Navigator) : LifecycleViewModel() {

    val currentTab = ObservableField(R.id.home_rooms)
    val topBannerViewModel = TopBannerViewModel(appComponent, appContext, navigator).let(this::addChildModel)

    fun onClickTab(id : Int) {
        currentTab.set(id)
    }

    interface Navigator : TopBannerViewModel.Navigator
}