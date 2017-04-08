package com.xianzhitech.ptt.ui.chat

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentChatBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment

class ChatFragment : BaseViewModelFragment<ChatViewModel, FragmentChatBinding>(), ChatViewModel.Navigator {
    private val chatAdapter = ChatAdapter()

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChatBinding {
        val binding = FragmentChatBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = chatAdapter
        return binding
    }

    override fun onCreateViewModel(): ChatViewModel {
        val model = ChatViewModel(appComponent, arguments.getString(ARG_ROOM_ID), this)
        model.roomMessages.addOnListChangedCallback(chatAdapter.listChangeListener)
        return model
    }

    override fun navigateToWalkieTalkie(roomId: String) {
    }

    override fun navigateToLatestMessageIfPossible() {
        val position = (dataBinding.recyclerView.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
        if (position == chatAdapter.itemCount - 2) {
            dataBinding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    companion object {
        const val ARG_ROOM_ID = "room_id"

        fun createInstance(roomId: String) : ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_ROOM_ID, roomId)
                }
            }
        }
    }
}