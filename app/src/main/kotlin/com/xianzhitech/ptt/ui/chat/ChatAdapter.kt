package com.xianzhitech.ptt.ui.chat

import android.databinding.DataBindingUtil
import android.databinding.ObservableList
import android.databinding.ObservableList.OnListChangedCallback
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.ViewMessageItemBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.model.Message


class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageHolder>() {
    private var messages = emptyList<Message>()

    val listChangeListener = object : OnListChangedCallback<ObservableList<Message>>() {
        override fun onItemRangeRemoved(list: ObservableList<Message>, position: Int, itemCount: Int) {
            messages = list
            notifyItemRangeRemoved(position, itemCount)
        }

        override fun onItemRangeMoved(list: ObservableList<Message>, position: Int, itemCount: Int, p3: Int) {
            messages = list
            notifyDataSetChanged()
        }

        override fun onItemRangeChanged(list: ObservableList<Message>, position: Int, itemCount: Int) {
            messages = list
            notifyItemRangeChanged(position, itemCount)
        }

        override fun onChanged(list: ObservableList<Message>) {
            messages = list
            notifyDataSetChanged()
        }

        override fun onItemRangeInserted(list: ObservableList<Message>, position: Int, itemCount: Int) {
            messages = list
            notifyItemRangeInserted(position, itemCount)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when (msg.type) {
            "text" -> R.layout.view_message_text
            else -> R.layout.view_message_text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatAdapter.MessageHolder {
        return MessageHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: ChatAdapter.MessageHolder, position: Int) {
        val msg = messages[position]
        holder.actualBinding.setVariable(BR.message, msg)
        holder.itemBinding.userId = msg.senderId
        holder.itemBinding.isMe = holder.itemView.context.appComponent.signalHandler.peekCurrentUserId == msg.senderId

        holder.actualBinding.executePendingBindings()
        holder.itemBinding.executePendingBindings()
    }

    private fun Long.formatMessageSentTime(): CharSequence {
        return DateUtils.getRelativeTimeSpanString(this, System.currentTimeMillis(), 60000L)
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    class MessageHolder(parent: ViewGroup, layout : Int) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_message_item, parent, false)) {
        val itemBinding : ViewMessageItemBinding = ViewMessageItemBinding.bind(itemView)
        val actualBinding : ViewDataBinding = DataBindingUtil.inflate(LayoutInflater.from(parent.context), layout, itemBinding.item, true)
    }
}