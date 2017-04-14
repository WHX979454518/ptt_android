package com.xianzhitech.ptt.api.exception


data class ServerException(val name: String,
                           override val message: String?) : RuntimeException(name)