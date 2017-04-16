package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonProperty


data class UserCredentials(@param:JsonProperty("name") val name: String,
                           @param:JsonProperty("password") val password: String)