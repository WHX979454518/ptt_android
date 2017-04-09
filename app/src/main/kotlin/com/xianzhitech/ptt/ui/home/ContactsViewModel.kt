package com.xianzhitech.ptt.ui.home

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ui.modellist.ModelListViewModel


class ContactsViewModel(private val modelProvider: ModelProvider,
                        private val appComponent: AppComponent,
                        private val navigator: Navigator? = null): ModelListViewModel(modelProvider, navigator) {

    interface Navigator {
        fun navigateToChatRoom()
    }
}