package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonProperty
import org.webrtc.IceCandidate


data class IceCandidateEvent(@param:JsonProperty("sdpMid") val sdpMid: String,
                             @param:JsonProperty("sdpMLineIndex") val sdpMLineIndex: Int,
                             @param:JsonProperty("candidate") val candidate: String) : Event {

    fun toWebrtc(): IceCandidate {
        return IceCandidate(sdpMid, sdpMLineIndex, candidate)
    }
}