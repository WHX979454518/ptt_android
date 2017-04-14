package com.xianzhitech.ptt.api.event


data class ConnectionErrorEvent(val err : Throwable) : Event