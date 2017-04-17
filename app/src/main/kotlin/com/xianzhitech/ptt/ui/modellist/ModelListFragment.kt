package com.xianzhitech.ptt.ui.modellist

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.observe
import com.xianzhitech.ptt.ext.show
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.widget.SideNavigationView


abstract class ModelListFragment<VM : ModelListViewModel, VB : ViewDataBinding> :
        BaseViewModelFragment<VM, VB>(),
        ModelListAdapter.Callbacks {
    protected lateinit var adapter: ModelListAdapter

    private var doneItem: MenuItem? = null
    private var searchItem: MenuItem? = null

    protected abstract val recyclerView : RecyclerView
    protected abstract val sideNavigationView : SideNavigationView
    protected abstract val currentCharView : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()

        if (viewModel.selectable) {
            viewModel.selectedItemIds
                    .observe()
                    .subscribe {
                        doneItem?.isEnabled = it.isNotEmpty()
                        doneItem?.title = if (it.isEmpty()) {
                            getString(R.string.finish)
                        } else {
                            getString(R.string.finish_with_number, viewModel.selectedItemIds.size)
                        }
                    }
                    .bindToLifecycle()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.model_list, menu)

        searchItem = menu.findItem(R.id.modelList_search)
        doneItem = menu.findItem(R.id.modelList_done).apply { isVisible = viewModel.selectable }

        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (viewModel.searchTerm.get() != newText) {
                    viewModel.searchTerm.set(newText)
                }

                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.modelList_done) {
            callbacks<Callbacks>()?.onSelectionDone(viewModel.selectedItemIds.keys.toList())
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = onCreateModelListAdapter()
        viewModel.viewModels.addOnListChangedCallback(adapter.listChangeListener)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = this@ModelListFragment.adapter

        sideNavigationView.onNavigateListener = object : SideNavigationView.OnNavigationListener {
            override fun onNavigateTo(c: String) {
                currentCharView.show = true
                currentCharView.text = c
                val position = viewModel.headerViewModelPositions.get()[c.toLowerCase()[0]]
                if (position != null) {
                    (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                }
            }

            override fun onNavigateCancel() {
                currentCharView.show = false
            }
        }
    }

    override fun onDestroyViewBinding() {
        super.onDestroyViewBinding()

        viewModel.viewModels.removeOnListChangedCallback(adapter.listChangeListener)
    }

    open fun onCreateModelListAdapter(): ModelListAdapter {
        return ModelListAdapter(this, R.layout.view_model_list_item)
    }

    override fun onClickModelItem(model: NamedModel) {
        viewModel.onClickItem(model)
    }

    interface Callbacks {
        fun onSelectionDone(selected: List<String>)
    }

    companion object {
        const val RESULT_EXTRA_SELECTED_IDS = "selected_ids"
    }
}