package com.xianzhitech.ptt.ui.chat

import android.os.Bundle
import com.xianzhitech.ptt.databinding.ActivityChatBinding
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ChatActivity : BaseToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityChatBinding.inflate(layoutInflater)

        setContentView(binding.root)
    }
}