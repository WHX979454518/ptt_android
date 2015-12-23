package com.xianzhitech.ptt.engine

/**
 * 创建语音引擎的工厂类

 * Created by fanchao on 13/12/15.
 */
interface TalkEngineProvider {
    fun createEngine(): TalkEngine
}
