package com.xianzhitech.ptt.ui.modellist

import android.view.ViewGroup
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.ui.util.ViewBindingAdapter
import com.xianzhitech.ptt.ui.util.ViewBindingHolder
import com.xianzhitech.ptt.viewmodel.ViewModel


class ModelListAdapter(private val callbacks: Callbacks,
                       private val itemLayoutId: Int) : ViewBindingAdapter() {
    override fun layoutFor(viewModel: ViewModel): Int {
        if (viewModel is HeaderViewModel) {
            return R.layout.view_section_header
        } else {
            return itemLayoutId
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder {
        return super.onCreateViewHolder(parent, viewType).apply {
            itemView.setOnClickListener {
                if (adapterPosition >= 0) {
                    val viewModel = viewModels[adapterPosition]
                    if (viewModel is ContactModelViewModel) {
                        callbacks.onClickModelItem(viewModel.model)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewBindingHolder, position: Int) {
        holder.dataBinding.setVariable(BR.viewModel, viewModels[position])
        holder.dataBinding.executePendingBindings()
    }

    override fun onViewRecycled(holder: ViewBindingHolder) {
        super.onViewRecycled(holder)
        holder.dataBinding.unbind()
    }

    interface Callbacks {
        fun onClickModelItem(model: NamedModel)
    }
}