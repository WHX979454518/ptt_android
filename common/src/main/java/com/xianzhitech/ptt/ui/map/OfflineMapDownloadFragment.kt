package com.xianzhitech.ptt.ui.map

import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.FragmentOfflineMapDownloadBinding
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.util.ViewBindingAdapter
import com.xianzhitech.ptt.ui.util.ViewBindingHolder
import com.xianzhitech.ptt.viewmodel.OfflineMapDownloadViewModel
import com.xianzhitech.ptt.viewmodel.ViewModel


class OfflineMapDownloadFragment : BaseViewModelFragment<OfflineMapDownloadViewModel, FragmentOfflineMapDownloadBinding>() {
    private val adapter = Adapter()

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentOfflineMapDownloadBinding {
        return FragmentOfflineMapDownloadBinding.inflate(inflater, container, false).apply {
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onCreateViewModel(): OfflineMapDownloadViewModel {
        return OfflineMapDownloadViewModel().apply {
            cityViewModels.addOnListChangedCallback(adapter.listChangeListener)
        }
    }

    private class Adapter : ViewBindingAdapter() {
        override fun layoutFor(viewModel: ViewModel): Int {
            return R.layout.view_offline_city_item
        }

        override fun onBindViewHolder(holder: ViewBindingHolder, position: Int) {
            holder.dataBinding.setVariable(BR.viewModel, viewModels[position])
            holder.dataBinding.executePendingBindings()
        }
    }
}