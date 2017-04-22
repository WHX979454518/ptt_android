package com.xianzhitech.ptt.api.event

import com.fasterxml.jackson.annotation.JsonProperty


data class LoginFailedEvent(@JsonProperty("name") val name: String,
                            @JsonProperty("message") val message: String?) : Event