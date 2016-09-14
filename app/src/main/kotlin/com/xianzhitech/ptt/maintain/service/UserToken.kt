package com.xianzhitech.ptt.maintain.service

import com.google.gson.annotations.SerializedName

data class UserToken(@SerializedName("user_id") val userId: String,
                     @SerializedName("password") val password: String)