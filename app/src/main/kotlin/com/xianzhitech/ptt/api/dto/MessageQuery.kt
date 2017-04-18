package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty


data class MessageQuery(@param:JsonProperty("roomId") val roomId: String? = null,
                        @param:JsonProperty("startTime") val startTime: Long? = null,
                        @param:JsonProperty("endTime") val endTime: Long? = null)