package com.xianzhitech.ptt.ui.widget

import android.support.v7.widget.RecyclerView

abstract class RecyclerAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    var data : List<T> = emptyList()
    set(value) {
        if (field.isEmpty() && value.isNotEmpty()) {
            field = value
            notifyItemRangeInserted(0, value.size)
        } else if (field.isNotEmpty() && value.isEmpty()) {
            val oldItemSize = field.size
            field = value
            notifyItemRangeRemoved(0, oldItemSize)
        } else if (field !== value) {
            field = value
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = data.size
}