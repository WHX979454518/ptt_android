package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.data.Message
import java.util.*


data class MessageQueryResult(@param:JsonProperty("syncTime") val syncTime: Date,
                              @param:JsonProperty("roomId") val roomId: String?,
                              @param:JsonProperty("data") val data: List<Message>)