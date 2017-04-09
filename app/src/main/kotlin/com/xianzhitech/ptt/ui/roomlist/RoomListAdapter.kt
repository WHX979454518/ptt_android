package com.xianzhitech.ptt.ui.roomlist

import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.util.ViewBindingAdapter
import com.xianzhitech.ptt.ui.util.ViewBindingHolder
import com.xianzhitech.ptt.ui.util.ViewModel


class RoomListAdapter : ViewBindingAdapter() {
    override fun layoutFor(viewModel: ViewModel): Int {
        return R.layout.view_room_item
    }

    override fun onBindViewHolder(holder: ViewBindingHolder, position: Int) {
        holder.dataBinding.setVariable(BR.viewModel, viewModels[position])
    }
}