package com.xianzhitech.ptt.service

import java.io.Serializable

interface UserToken : Serializable {
    val userId: String
}