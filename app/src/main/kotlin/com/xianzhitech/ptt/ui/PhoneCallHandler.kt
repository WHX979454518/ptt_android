package com.xianzhitech.ptt.ui

import android.content.Context
import android.telephony.TelephonyManager
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.receiveBroadcasts
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import rx.Observable
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 处理电话和对讲交互的模块
 */
class PhoneCallHandler private constructor(private val appContext: Context) {
    companion object {
        private var registered = AtomicBoolean(false)

        fun register(context: Context) {
            if (registered.compareAndSet(false, true)) {
                PhoneCallHandler(context.applicationContext)
            }
        }
    }

    private val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val iTelephony: Any? by lazy {
        try {
            telephonyManager.javaClass.getDeclaredMethod("getITelephony").let {
                it.isAccessible = true
                it.invoke(telephonyManager)
            }
        } catch(e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private val endCallMethod: Method? by lazy {
        try {
            iTelephony?.javaClass?.getDeclaredMethod("endCall")?.apply {
                isAccessible = true
            }
        } catch(e: Exception) {
            e.printStackTrace()
            null
        }
    }

    init {
        val appComponent = appContext as AppComponent
        Observable.combineLatest(
                appContext.receiveBroadcasts(false, TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                        .map { telephonyManager.callState }
                        .startWith(telephonyManager.callState),
                appComponent.signalHandler.roomState,
                { callState, roomState -> callState to roomState }
        ).subscribe {
            onCallStateChanged(it.first, it.second)
        }
    }

    private fun onCallStateChanged(callState: Int, roomState: RoomState) {
        if (callState == TelephonyManager.CALL_STATE_RINGING &&
                EnumSet.of(RoomStatus.ACTIVE, RoomStatus.JOINED, RoomStatus.JOINING).contains(roomState.status) &&
                (appContext as AppComponent).preference.blockCalls) {
            endCallMethod?.invoke(iTelephony)
        }
    }
}