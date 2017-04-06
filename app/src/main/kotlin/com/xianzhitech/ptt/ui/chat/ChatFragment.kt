package com.xianzhitech.ptt.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentChatBinding
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment

class ChatFragment : BaseViewModelFragment<ChatViewModel, FragmentChatBinding>() {
    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChatBinding {
        return FragmentChatBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel(): ChatViewModel {
        return ChatViewModel()
    }
}