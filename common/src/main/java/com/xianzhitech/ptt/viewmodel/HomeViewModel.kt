package com.xianzhitech.ptt.viewmodel

import android.content.Context
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.i


class HomeViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    navigator: Navigator) : LifecycleViewModel() {

    val currentTab = ObservableField(R.id.home_rooms)
    val topBannerViewModel = TopBannerViewModel(appComponent, appContext, navigator).let(this::addChildModel)

    override fun onStart() {
        super.onStart()

        appComponent.signalBroker
                .onlineUserIds
                .subscribe {
                    logger.i { "Got online users $it" }
                }
    }

    interface Navigator : TopBannerViewModel.Navigator
}