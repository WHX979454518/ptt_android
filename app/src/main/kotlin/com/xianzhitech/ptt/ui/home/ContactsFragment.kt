package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.defaultOnErrorAction
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.toRx
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.ui.modellist.ModelListAdapter
import com.xianzhitech.ptt.ui.modellist.NewModelListFragment
import rx.CompletableSubscriber
import rx.Observable
import rx.Subscription

class ContactsFragment : NewModelListFragment() {
    private var swipeRefreshLayout : SwipeRefreshLayout? = null

    override val modelProvider: ModelProvider = object : BaseModelProvider(false, emptyList(), false) {
        override fun getModels(context: Context): Observable<List<NamedModel>> {
            return appComponent.contactRepository.getContactItems().observe()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.contacts, menu)
        val searchView = MenuItemCompat.getActionView(menu.findItem(R.id.contacts_search)) as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (viewModel.searchTerm.get() != newText) {
                    viewModel.searchTerm.set(newText)
                }
                return true
            }
        })

        viewModel.searchTerm.toRx()
                .subscribe { searchView.setQuery(it, false) }
                .bindToLifecycle()
    }
}
