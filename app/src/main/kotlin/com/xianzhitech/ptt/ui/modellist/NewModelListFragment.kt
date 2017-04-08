package com.xianzhitech.ptt.ui.modellist

import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.FragmentNewModelListBinding
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.show
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.home.ModelProvider
import com.xianzhitech.ptt.ui.widget.SideNavigationView


open class NewModelListFragment : BaseViewModelFragment<ModelListViewModel, FragmentNewModelListBinding>(), ModelListAdapter.Callbacks {
    protected lateinit var adapter : ModelListAdapter

    open val modelProvider : ModelProvider
    get() = arguments.getParcelable(ARG_MODEL_PROVIDER)

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNewModelListBinding {
        adapter = onCreateModelListAdapter()
        viewModel.viewModels.addOnListChangedCallback(adapter.listChangeListener)

        return FragmentNewModelListBinding.inflate(inflater, container, false).apply {
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = this@NewModelListFragment.adapter

            sideBar.onNavigateListener = object : SideNavigationView.OnNavigationListener {
                override fun onNavigateTo(c: String) {
                    currentChar.show = true
                    currentChar.text = c
                    val position = viewModel.headerViewModelPositions.get()[c[0]]
                    if (position != null) {
                        recyclerView.scrollToPosition(position)
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

    override fun onCreateViewModel(): ModelListViewModel {
        return ModelListViewModel(modelProvider, callbacks())
    }

    override fun onClickModelItem(model: NamedModel) {
        viewModel.onClickItem(model)
    }

    interface Callbacks : ModelListViewModel.Navigator

    companion object {
        const val ARG_MODEL_PROVIDER = "model_provider"
    }
}