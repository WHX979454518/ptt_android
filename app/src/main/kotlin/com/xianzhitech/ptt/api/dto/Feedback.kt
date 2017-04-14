package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

class Feedback(@JsonProperty("title") val title: String,
               @JsonProperty("message") val message: String,
               @JsonProperty("user_id") val userId: String?)