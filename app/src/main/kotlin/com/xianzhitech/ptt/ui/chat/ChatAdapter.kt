package com.xianzhitech.ptt.ui.chat

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.common.collect.ComparisonChain
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.databinding.ViewMessageItemBinding
import com.xianzhitech.ptt.ext.appComponent


class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageHolder>() {
    val messages = SortedList<Message>(Message::class.java, object : SortedListAdapterCallback<Message>(this) {
        override fun areContentsTheSame(oldItem: Message?, newItem: Message): Boolean {
            return oldItem == newItem
        }

        override fun compare(o1: Message, o2: Message): Int {
            if (areItemsTheSame(o1, o2)) {
                return 0
            }

            return ComparisonChain.start()
                    .compare(o2.sendTime, o1.sendTime)
                    .compare(o2.id, o1.id)
                    .result()
        }

        override fun areItemsTheSame(item1: Message, item2: Message): Boolean {
            if (hasNonNullItem(item1.id, item2.id) && item1.id == item2.id) {
                return true
            }

            if (hasNonNullItem(item1.localId, item2.localId) && item1.localId == item2.localId) {
                return true
            }

            if (hasNonNullItem(item1.remoteId, item2.remoteId) && item1.remoteId == item2.remoteId) {
                return true
            }

            return false
        }

        private fun hasNonNullItem(o1 : Any?, o2 : Any?) : Boolean {
            return o1 != null || o2 != null
        }
    })

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when (msg.type) {
            MessageType.TEXT -> R.layout.view_message_text
            else -> R.layout.view_message_text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatAdapter.MessageHolder {
        return MessageHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: ChatAdapter.MessageHolder, position: Int) {
        val msg = messages[position]
        holder.actualBinding.setVariable(BR.body, msg.body)
        holder.actualBinding.setVariable(BR.message, msg)
        holder.itemBinding.userId = msg.senderId
        holder.itemBinding.isMe = holder.itemView.context.appComponent.signalBroker.peekUserId() == msg.senderId

        holder.actualBinding.executePendingBindings()
        holder.itemBinding.executePendingBindings()
    }

    private fun Long.formatMessageSentTime(): CharSequence {
        return DateUtils.getRelativeTimeSpanString(this, System.currentTimeMillis(), 60000L)
    }

    override fun getItemCount(): Int {
        return messages.size()
    }

    class MessageHolder(parent: ViewGroup, layout : Int) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_message_item, parent, false)) {
        val itemBinding : ViewMessageItemBinding = ViewMessageItemBinding.bind(itemView)
        val actualBinding : ViewDataBinding = DataBindingUtil.inflate(LayoutInflater.from(parent.context), layout, itemBinding.item, true)
    }
}