package com.xianzhitech.ptt.ui

import android.content.Context
import android.preference.PreferenceManager
import android.telephony.TelephonyManager
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.receiveBroadcasts
import com.xianzhitech.ptt.service.RoomState
import rx.Observable
import java.util.*

/**
 * 处理电话和对讲交互的模块
 */
class PhoneCallHandler private constructor(private val appContext : Context) {
    companion object {
        fun register(context: Context) {
            PhoneCallHandler(context.applicationContext)
        }
    }

    private val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    init {
        val appComponent = appContext as AppComponent
        Observable.combineLatest(
                appContext.receiveBroadcasts(false, TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                        .map { telephonyManager.callState }
                        .startWith(telephonyManager.callState),
                appComponent.connectToBackgroundService()
                        .flatMap { it.roomState },
                { callState, roomState -> callState to roomState }
        ).subscribe {
            onCallStateChanged(it.first, it.second)
        }
    }

    internal fun onCallStateChanged(callState: Int, roomState : RoomState) {
        if (callState == TelephonyManager.CALL_STATE_RINGING &&
                EnumSet.of(RoomState.Status.ACTIVE, RoomState.Status.JOINED, RoomState.Status.JOINING).contains(roomState.status) &&
                PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean("block_call", true)) {

            try {
                telephonyManager.javaClass.getDeclaredMethod("getITelephony").let {
                    it.isAccessible = true
                    it.invoke(telephonyManager)
                }.let {
                    it.javaClass.getDeclaredMethod("endCall").invoke(it)
                }
            }
            catch (e : Throwable) {
                e.printStackTrace()
            }
        }
    }
}