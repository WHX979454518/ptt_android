package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.defaultOnErrorAction
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.ui.chat.ChatActivity
import com.xianzhitech.ptt.ui.modellist.ModelListAdapter
import com.xianzhitech.ptt.ui.modellist.ModelListFragment
import rx.CompletableSubscriber
import rx.Observable
import rx.Subscription

class ContactsFragment : ModelListFragment<ContactsViewModel>(), ContactsViewModel.Navigator {
    private var swipeRefreshLayout : SwipeRefreshLayout? = null

    override val modelProvider: ModelProvider = object : BaseModelProvider(false, emptyList(), false) {
        override fun getModels(context: Context): Observable<List<NamedModel>> {
            return appComponent.contactRepository.getContactItems().observe()
        }
    }

    override fun navigateToChatRoom(room: Room) {
        startActivity(ChatActivity.createIntent(room.id))
    }

    override fun onCreateViewModel(): ContactsViewModel {
        return ContactsViewModel(modelProvider, appComponent, this)
    }

    override fun onCreateModelListAdapter(): ModelListAdapter {
        return ModelListAdapter(this, R.layout.view_contact_item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        swipeRefreshLayout = SwipeRefreshLayout(context)
        swipeRefreshLayout!!.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        swipeRefreshLayout!!.addView(super.onCreateView(inflater, swipeRefreshLayout, savedInstanceState))
        swipeRefreshLayout!!.setOnRefreshListener {
            refresh()
        }
        return swipeRefreshLayout
    }

    private fun refresh() {
        swipeRefreshLayout?.isRefreshing = true
        appComponent.signalHandler.syncContact()
                .observeOnMainThread()
                .subscribe(object : CompletableSubscriber {
                    override fun onError(e: Throwable) {
                        defaultOnErrorAction.call(e)
                        swipeRefreshLayout?.isRefreshing = false
                    }

                    override fun onCompleted() {
                        swipeRefreshLayout?.isRefreshing = false
                        Toast.makeText(context, R.string.contact_updated, Toast.LENGTH_LONG).show()
                    }

                    override fun onSubscribe(d: Subscription) {
                        d.bindToLifecycle()
                    }
                })
    }
}
