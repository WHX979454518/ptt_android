package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.defaultOnErrorAction
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.NamedModel
import rx.CompletableSubscriber
import rx.Observable
import rx.Subscription

class ContactsFragment : ModelListFragment() {
    private var swipeRefreshLayout : SwipeRefreshLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
                search(newText)
                return true
            }
        })
    }

    override val allModels: Observable<List<NamedModel>>
        get() = appComponent.contactRepository.getContactItems().observe()

    override fun onCreateModelViewHolder(container: ViewGroup): RecyclerView.ViewHolder {
        return ModelItemHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false))
    }

    override fun onBindModelViewHolder(viewHolder: RecyclerView.ViewHolder, model: NamedModel) {
        (viewHolder as ModelItemHolder).model = model
    }
}
