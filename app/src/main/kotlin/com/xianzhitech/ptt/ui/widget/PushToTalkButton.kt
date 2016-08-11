package com.xianzhitech.ptt.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageButton
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.handler.SignalServiceHandler
import org.slf4j.LoggerFactory
import rx.Subscription
import rx.subscriptions.CompositeSubscription


private val logger = LoggerFactory.getLogger("PushToTalkButton")

/**

 * 带有阴影和效果的对讲按钮

 * Created by fanchao on 12/12/15.
 */
class PushToTalkButton : ImageButton {
    private lateinit var signalService: SignalServiceHandler
    private var subscription: Subscription? = null
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val requestFocusRunnable = Runnable { signalService.requestMic().subscribeSimple() }
    private var vibrator: Vibrator? = null
    private var isPressingDown = false
    private var ringPadding: Float = 0f

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
        if (isInEditMode.not()) {
            vibrator = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let {
                if (it.hasVibrator()) {
                    it
                } else {
                    null
                }
            }
        }

        ringPadding = R.dimen.divider_thick.toDimen(context)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = R.dimen.button_stroke.toDimen(context)
        if (isInEditMode.not()) {
            signalService = context.appComponent.signalHandler
        }
        applyRoomStatus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode.not()) {
            subscription = CompositeSubscription().apply {
                if (vibrator != null) {
                    this.add(signalService.roomState.distinctUntilChanged { it -> it.status }
                            .observeOnMainThread()
                            .subscribeSimple {
                                if (isPressingDown && it.status == RoomStatus.ACTIVE && it.speakerId == signalService.currentUserId) {
                                    vibrator!!.vibrate(120)
                                }
                            })
                }

                add(signalService.roomStatus.observeOnMainThread().subscribeSimple {
                    applyRoomStatus()
                    invalidate()
                })
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscription?.unsubscribe()
        subscription = null
        super.onDetachedFromWindow()
    }

    private fun applyRoomStatus() {
        isEnabled = when (signalService.peekRoomState().status) {
            RoomStatus.IDLE, RoomStatus.OFFLINE -> false
            else -> true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val roomState = signalService.peekRoomState()
        val loginState = signalService.peekLoginState()
        val roomStatus = roomState.status
        logger.d { "Paint ptt button roomStatus: $roomStatus" }

        paint.color = when {
            roomStatus == RoomStatus.JOINED ||
                    (roomStatus == RoomStatus.ACTIVE && roomState.canRequestMic(loginState.currentUser)) -> android.R.color.holo_green_dark
            roomStatus == RoomStatus.ACTIVE -> android.R.color.holo_red_dark
            roomStatus == RoomStatus.REQUESTING_MIC -> android.R.color.holo_orange_dark
            else -> android.R.color.darker_gray
        }.toColorValue(context)

        canvas.drawCircle(width / 2f, height / 2f, (width - paint.strokeWidth) / 2f - ringPadding, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEnabled) {
                    isPressingDown = true
                    postDelayed(requestFocusRunnable, 100)
                    return true
                } else return false
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                removeCallbacks(requestFocusRunnable)
                signalService.releaseMic()
                isPressingDown = false
            }
        }

        return true
    }
}
