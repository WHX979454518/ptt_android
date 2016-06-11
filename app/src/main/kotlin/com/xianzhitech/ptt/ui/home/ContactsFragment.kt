package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.model.Model
import rx.Observable

class ContactsFragment : ModelListFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.contacts, menu)
        val searchView = menu.findItem(R.id.contacts_search).actionView as SearchView
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

    override val allModels: Observable<List<Model>>
        get() = appComponent.contactRepository.getContactItems().observe()

    override fun onCreateModelViewHolder(container: ViewGroup): RecyclerView.ViewHolder {
        return ModelItemHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false))
    }

    override fun onBindModelViewHolder(viewHolder: RecyclerView.ViewHolder, model: Model) {
        (viewHolder as ModelItemHolder).model = model
    }
}
