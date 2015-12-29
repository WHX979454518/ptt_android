package com.xianzhitech.ptt.engine


import com.xianzhitech.ptt.model.RoomInfo

/**

 * 定义对讲引擎操作的接口

 * Created by fanchao on 13/12/15.
 */
interface TalkEngine {
    fun connect(roomInfo: RoomInfo)
    fun dispose()
    fun startSend()
    fun stopSend()
}
