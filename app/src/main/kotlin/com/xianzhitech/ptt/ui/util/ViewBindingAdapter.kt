package com.xianzhitech.ptt.ui.util

import android.databinding.DataBindingUtil
import android.databinding.ObservableList
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.viewmodel.ViewModel


abstract class ViewBindingAdapter : RecyclerView.Adapter<ViewBindingHolder>() {
    val listChangeListener : ObservableList.OnListChangedCallback<ObservableList<ViewModel>> = ListChangeListener()

    var viewModels: List<ViewModel> = emptyList()
    private set

    @LayoutRes
    abstract fun layoutFor(viewModel: ViewModel) : Int

    override fun getItemViewType(position: Int): Int {
        return layoutFor(viewModels[position])
    }

    override fun getItemCount(): Int {
        return viewModels.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder {
        return ViewBindingHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), viewType, parent, false))
    }

    private inner class ListChangeListener : ObservableList.OnListChangedCallback<ObservableList<ViewModel>>() {
        override fun onItemRangeRemoved(list: ObservableList<ViewModel>, startIndex: Int, itemCount: Int) {
            this@ViewBindingAdapter.viewModels = list
            notifyItemRangeRemoved(startIndex, itemCount)
        }

        override fun onItemRangeInserted(list: ObservableList<ViewModel>, startIndex: Int, itemCount: Int) {
            this@ViewBindingAdapter.viewModels = list
            notifyItemRangeInserted(startIndex, itemCount)
        }

        override fun onChanged(list: ObservableList<ViewModel>) {
            this@ViewBindingAdapter.viewModels = list
            notifyDataSetChanged()
        }

        override fun onItemRangeChanged(list: ObservableList<ViewModel>, startIndex: Int, itemCount: Int) {
            this@ViewBindingAdapter.viewModels = list
            notifyItemRangeChanged(startIndex, itemCount)
        }

        override fun onItemRangeMoved(list: ObservableList<ViewModel>, startIndex: Int, itemCount: Int, p3: Int) {
            this@ViewBindingAdapter.viewModels = list
            notifyDataSetChanged()
        }

    }
}