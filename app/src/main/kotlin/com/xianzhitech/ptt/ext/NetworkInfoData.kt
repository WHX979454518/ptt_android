package com.xianzhitech.ptt.ext

import android.net.NetworkInfo

/**
 * To wrap around NetworkInfo so 'equals' can be implemented
 * Created by fanchao on 17/02/16.
 */
data class NetworkInfoData(private val networkType : Int,
                           private val subType : Int,
                           private val typeName : String?,
                           private val subtypeName : String?,
                           private val state : NetworkInfo.State?,
                           private val detailedState: NetworkInfo.DetailedState?,
                           private val reason : String?,
                           private val extraInfo : String?,
                           private val isFailover : Boolean?,
                           private val isRomaing : Boolean?) {

    constructor(info: NetworkInfo):this(
            info.type, info.subtype, info.typeName, info.subtypeName,
            info.state, info.detailedState, info.reason, info.extraInfo,
            info.isFailover, info.isRoaming)

    val isConnected : Boolean
    get() = state == NetworkInfo.State.CONNECTED
}