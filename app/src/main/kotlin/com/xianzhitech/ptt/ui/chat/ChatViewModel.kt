package com.xianzhitech.ptt.ui.chat

import android.databinding.ObservableField
import com.xianzhitech.ptt.ui.base.LifecycleViewModel


class ChatViewModel : LifecycleViewModel() {
    val message = ObservableField<String>()

    val displaySend : Boolean = false
    val displayMore : Boolean = true

    fun onClickCall() {

    }

    fun onClickEmoji() {

    }

    fun onClickSend() {

    }

    fun onClickMore() {

    }

}