package com.xianzhitech.ptt.model

/**
 * Created by fanchao on 18/12/15.
 */
data class Room(val id: Int, val members: List<String>, val speaker: String?, val serverHost: String, val severPort: Int, val serverProtocol: String)