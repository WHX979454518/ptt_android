package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty


data class MessageQuery(@param:JsonProperty("roomId") val roomId: String,
                        @param:JsonProperty("startTime") val startTime: Long?,
                        @param:JsonProperty("endTime") val endTime: Long?)