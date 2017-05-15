package com.xianzhitech.ptt.viewmodel

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Permission


class HomeViewModel(private val appComponent: AppComponent,
                    appContext: Context,
                    private val navigator: Navigator) : LifecycleViewModel() {

    val currentTab = ObservableField(R.id.home_rooms)
    val topBannerViewModel = TopBannerViewModel(appComponent, appContext, navigator).let(this::addChildModel)
    val displayMapMenu = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        appComponent.signalBroker
                .currentUser
                .map { it.orNull()?.hasPermission(Permission.VIEW_MAP) ?: false  }
                .distinctUntilChanged()
                .subscribe(displayMapMenu::set)
                .bindToLifecycle()
    }

    fun onClickMapView() {
        navigator.navigateToNearByPage()
    }

    interface Navigator : TopBannerViewModel.Navigator {
        fun navigateToNearByPage()
    }
}