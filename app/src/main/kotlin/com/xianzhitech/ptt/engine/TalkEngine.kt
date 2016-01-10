package com.xianzhitech.ptt.engine


/**

 * 定义对讲引擎操作的接口

 * Created by fanchao on 13/12/15.
 */
interface TalkEngine {
    fun connect(roomId: String, property: Map<String, Any?>)
    fun dispose()
    fun startSend()
    fun stopSend()
}
