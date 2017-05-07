package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonProperty


data class LoginFailedEvent(@param:JsonProperty("name") val name: String?,
                            @param:JsonProperty("message") val message: String?) : Event