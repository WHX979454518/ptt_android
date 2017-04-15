package com.xianzhitech.ptt.data.exception

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty


data class ServerException @JsonCreator constructor(@param:JsonProperty("name") val name: String,
                                                    @param:JsonProperty("message") override val message: String?) : RuntimeException(name)