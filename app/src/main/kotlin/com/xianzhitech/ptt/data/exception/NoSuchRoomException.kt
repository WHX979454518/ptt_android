package com.xianzhitech.ptt.data.exception

data class NoSuchRoomException(val roomId: String?) : RuntimeException()