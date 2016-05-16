package com.xianzhitech.ptt.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageButton
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toColorValue
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.roomStatus
import rx.Subscription

/**

 * 带有阴影和效果的对讲按钮

 * Created by fanchao on 12/12/15.
 */
class PushToTalkButton : ImageButton {
    private lateinit var signalService : SignalService
    private var subscription : Subscription? = null
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var roomStatus : RoomStatus = RoomStatus.IDLE
    private val requestFocusRunnable = Runnable { signalService.requestMic().subscribeSimple() }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
    }

    private fun init(context: Context) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.resources.getDimension(R.dimen.button_stroke)
        if (isInEditMode.not()) {
            signalService = (context.applicationContext as AppComponent).signalService
        }
        applyRoomStatus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode.not()) {
            subscription = signalService.roomStatus
                    .observeOnMainThread()
                    .subscribeSimple {
                        if (roomStatus != it) {
                            roomStatus = it
                            applyRoomStatus()
                            invalidate()
                        }
                    }
        }
    }

    override fun onDetachedFromWindow() {
        subscription?.unsubscribe()
        subscription = null
        super.onDetachedFromWindow()
    }

    private fun applyRoomStatus() {
        isEnabled = when (roomStatus) {
            RoomStatus.IDLE, RoomStatus.OFFLINE -> false
            else -> true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        logd("Paint ptt button roomStatus: $roomStatus")

        paint.color = when(roomStatus) {
            RoomStatus.ACTIVE ->  android.R.color.holo_red_dark
            RoomStatus.REQUESTING_MIC -> android.R.color.holo_orange_dark
            RoomStatus.JOINED -> android.R.color.holo_green_dark
            else  -> android.R.color.darker_gray
        }.toColorValue(context)

        canvas.drawCircle(width / 2f, height / 2f, (width - paint.strokeWidth)/ 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (isEnabled) {
                postDelayed(requestFocusRunnable, 100)
                return true
            } else return false

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                removeCallbacks(requestFocusRunnable)
                signalService.releaseMic().subscribeSimple()
            }
        }

        return true;
    }
}
