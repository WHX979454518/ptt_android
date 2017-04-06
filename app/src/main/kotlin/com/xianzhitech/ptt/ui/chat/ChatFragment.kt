package com.xianzhitech.ptt.ui.chat

import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentChatBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment

class ChatFragment : BaseViewModelFragment<ChatViewModel, FragmentChatBinding>() {
    private val chatAdapter = ChatAdapter()

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChatBinding {
        val binding = FragmentChatBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.recyclerView.adapter = chatAdapter
        return binding
    }

    override fun onCreateViewModel(): ChatViewModel {
        val model = ChatViewModel(appComponent.signalHandler)
        model.roomMessages.addOnListChangedCallback(chatAdapter.listChangeListener)
        return model
    }
}