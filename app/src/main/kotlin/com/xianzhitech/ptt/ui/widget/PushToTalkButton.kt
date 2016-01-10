package com.xianzhitech.ptt.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageButton
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toColorValue

/**

 * 带有阴影和效果的对讲按钮

 * Created by fanchao on 12/12/15.
 */
class PushToTalkButton : ImageButton {
    var connectedToRoom: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isRequestingMic: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var hasActiveSpeaker: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    public var callbacks: Callbacks? = null

    private val requestFocusRunnable = Runnable {
        callbacks?.requestMic()
    }

    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        paint.color = (
                if (hasActiveSpeaker) android.R.color.holo_red_dark
                else if (isRequestingMic) android.R.color.holo_orange_dark
                else if (connectedToRoom) android.R.color.holo_green_dark
                else android.R.color.darker_gray
                ).toColorValue(context)

        canvas?.drawCircle(width / 2f, height / 2f, width / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> postDelayed(requestFocusRunnable, 100)

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                removeCallbacks(requestFocusRunnable)
                callbacks?.releaseMic()
            }
        }

        return super.onTouchEvent(event)
    }

    interface Callbacks {
        fun requestMic()
        fun releaseMic()
    }
}
