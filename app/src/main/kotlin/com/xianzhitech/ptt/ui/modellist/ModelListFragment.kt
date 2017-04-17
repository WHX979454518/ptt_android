package com.xianzhitech.ptt.ui.modellist

import android.app.Activity
import android.content.Intent
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
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
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
                        val selectedItems : Int
                        if (viewModel.preselectedUnselectable) {
                            selectedItems = viewModel.selectedItemIds.size - viewModel.preselectedIds.size
                        } else {
                            selectedItems = viewModel.selectedItemIds.size
                        }

                        doneItem?.isEnabled = selectedItems > 0
                        doneItem?.title = if (selectedItems <= 0) {
                            getString(R.string.finish)
                        } else {
                            getString(R.string.finish_with_number, selectedItems)
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
            onSelectDone()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    open fun onSelectDone() {
        val selected = ArrayList(viewModel.selectedItemIds.keys)
        if (activity is FragmentDisplayActivity) {
            activity.setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_EXTRA_SELECTED_IDS, selected))
            activity.finish()
        }
        else {
            callbacks<Callbacks>()?.onSelectionDone(selected)
        }
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
        fun onSelectionDone(ids : List<String>)
    }

    companion object {
        const val RESULT_EXTRA_SELECTED_IDS = "selected_ids"
    }
}