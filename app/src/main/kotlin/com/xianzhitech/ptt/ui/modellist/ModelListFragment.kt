package com.xianzhitech.ptt.ui.modellist

import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.*
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.FragmentModelListBinding
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.observe
import com.xianzhitech.ptt.ext.show
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.home.ModelProvider
import com.xianzhitech.ptt.ui.widget.SideNavigationView


open class ModelListFragment<T : ModelListViewModel> :
        BaseViewModelFragment<T, FragmentModelListBinding>(),
        ModelListAdapter.Callbacks {
    protected lateinit var adapter: ModelListAdapter

    open val modelProvider: ModelProvider
        get() = arguments.getParcelable(ARG_MODEL_PROVIDER)

    private var doneItem: MenuItem? = null
    private var searchItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()

        viewModel.selectedItemIds
                .observe()
                .subscribe {
                    doneItem?.isEnabled = it.isNotEmpty()
                    doneItem?.title = if (it.isEmpty()) {
                        getString(R.string.finish)
                    }
                    else {
                        getString(R.string.finish_with_number, viewModel.selectedItemIds.size)
                    }
                }
                .bindToLifecycle()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.model_list, menu)

        searchItem = menu.findItem(R.id.modelList_search)
        doneItem = menu.findItem(R.id.modelList_done).apply { isVisible = modelProvider.selectable }

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

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentModelListBinding {
        adapter = onCreateModelListAdapter()
        viewModel.viewModels.addOnListChangedCallback(adapter.listChangeListener)

        return FragmentModelListBinding.inflate(inflater, container, false).apply {
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = this@ModelListFragment.adapter

            sideBar.onNavigateListener = object : SideNavigationView.OnNavigationListener {
                override fun onNavigateTo(c: String) {
                    currentChar.show = true
                    currentChar.text = c
                    val position = viewModel.headerViewModelPositions.get()[c.toLowerCase()[0]]
                    if (position != null) {
                        (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                    }
                }

                override fun onNavigateCancel() {
                    currentChar.show = false
                }
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

    override fun onCreateViewModel(): T {
        @Suppress("UNCHECKED_CAST")
        return ModelListViewModel(modelProvider, callbacks()) as T
    }

    override fun onClickModelItem(model: NamedModel) {
        viewModel.onClickItem(model)
    }

    interface Callbacks : ModelListViewModel.Navigator {
        fun onSelectionDone(selected: List<String>)
    }

    companion object {
        const val ARG_MODEL_PROVIDER = "model_provider"
    }
}