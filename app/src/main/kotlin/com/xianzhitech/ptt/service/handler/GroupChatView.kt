package com.xianzhitech.ptt.service.handler

import org.webrtc.VideoRenderer

interface GroupChatView {
    val remoteRenderer : VideoRenderer?

    fun setElapsedTime(seconds : Long)
}