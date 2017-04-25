package com.xianzhitech.ptt.ui.chat

import android.databinding.DataBindingUtil
import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.common.collect.ComparisonChain
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.ui.util.ViewBindingHolder
import com.xianzhitech.ptt.viewmodel.MessageViewModel


class ChatAdapter : RecyclerView.Adapter<ViewBindingHolder>() {
    val messages = SortedList<MessageViewModel>(MessageViewModel::class.java, object : SortedListAdapterCallback<MessageViewModel>(this) {
        override fun areContentsTheSame(oldItem: MessageViewModel?, newItem: MessageViewModel): Boolean {
            return oldItem == newItem
        }

        override fun compare(o1: MessageViewModel, o2: MessageViewModel): Int {
            if (areItemsTheSame(o1, o2)) {
                return 0
            }

            return ComparisonChain.start()
                    .compare(o2.message.sendTime, o1.message.sendTime)
                    .compare(o2.message.id, o1.message.id)
                    .result()
        }

        override fun areItemsTheSame(item1: MessageViewModel, item2: MessageViewModel): Boolean {
            if (hasNonNullItem(item1.message.id, item2.message.id) && item1.message.id == item2.message.id) {
                return true
            }

            if (hasNonNullItem(item1.message.localId, item2.message.localId) && item1.message.localId == item2.message.localId) {
                return true
            }

            if (hasNonNullItem(item1.message.remoteId, item2.message.remoteId) && item1.message.remoteId == item2.message.remoteId) {
                return true
            }

            return false
        }

        private fun hasNonNullItem(o1: Any?, o2: Any?): Boolean {
            return o1 != null || o2 != null
        }
    })

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when (msg.message.type) {
            MessageType.TEXT -> {
                if (msg.isMyMessage) {
                    R.layout.view_message_text_me
                } else {
                    R.layout.view_message_text_other
                }
            }
            MessageType.IMAGE -> {
                if (msg.isMyMessage) {
                    R.layout.view_message_image_me
                } else {
                    R.layout.view_message_image_other
                }

            }
            MessageType.NOTIFY_JOIN_ROOM,
            MessageType.NOTIFY_QUIT_ROOM -> R.layout.view_message_notification
            else -> R.layout.view_message_unknown
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder {
        return ViewBindingHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), viewType, parent, false))
    }

    override fun onBindViewHolder(holder: ViewBindingHolder, position: Int) {
        val msg = messages[position]
        holder.dataBinding.setVariable(BR.viewModel, msg)
        holder.dataBinding.executePendingBindings()
    }

    override fun getItemCount(): Int {
        return messages.size()
    }

}