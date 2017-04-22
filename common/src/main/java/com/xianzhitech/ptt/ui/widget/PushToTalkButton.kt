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
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.d
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toColorValue
import com.xianzhitech.ptt.ext.toDimen
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.toast
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("PushToTalkButton")

/**

 * 带有阴影和效果的对讲按钮

 * Created by fanchao on 12/12/15.
 */
class PushToTalkButton : ImageButton {
    private lateinit var signalService: SignalBroker
    private var subscription: Disposable? = null
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val requestFocusRunnable = Runnable { signalService.grabWalkieMic().toMaybe().logErrorAndForget(Throwable::toast).subscribe() }
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
            signalService = context.appComponent.signalBroker
            applyRoomStatus()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode.not()) {
            subscription = CompositeDisposable().apply {
                if (vibrator != null) {
                    this.add(signalService.currentWalkieRoomState.distinctUntilChanged(RoomState::status)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                if (isPressingDown && it.status == RoomStatus.ACTIVE && it.speakerId == signalService.peekUserId()) {
                                    vibrator!!.vibrate(120)
                                }
                            })
                }

                add(signalService.currentWalkieRoomState
                        .cast(Any::class.java)
                        .mergeWith(signalService.connectionState)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            applyRoomStatus()
                            invalidate()
                        })
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscription?.dispose()
        subscription = null
        super.onDetachedFromWindow()
    }

    private fun applyRoomStatus() {
        isEnabled = signalService.currentWalkieRoomState.value.canRequestMic(signalService.currentUser.value.orNull()) &&
                signalService.connectionState.value == SignalApi.ConnectionState.CONNECTED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isInEditMode.not()) {
            val roomState = signalService.currentWalkieRoomState.value
            val roomStatus = roomState.status
            val connectionState = signalService.connectionState.value
            logger.d { "Paint ptt button roomStatus: $roomStatus" }

            paint.color = when {
                connectionState != SignalApi.ConnectionState.CONNECTED -> android.R.color.darker_gray
                roomState.canRequestMic(signalService.currentUser.value.orNull()) -> android.R.color.holo_green_dark
                roomStatus == RoomStatus.ACTIVE -> android.R.color.holo_red_dark
                roomStatus == RoomStatus.REQUESTING_MIC -> android.R.color.holo_orange_dark
                else -> android.R.color.darker_gray
            }.toColorValue(context)
        }

        canvas.drawCircle(width / 2f, height / 2f, (width - paint.strokeWidth) / 2f - ringPadding, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEnabled) {
                    isPressingDown = true
                    postDelayed(requestFocusRunnable, 100)
                    return true
                } else if (signalService.isLoggedIn) {
                    if (signalService.currentUser.value.get().hasPermission(Permission.SPEAK).not()) {
                        Toast.makeText(context, R.string.error_user_no_permission_to_speak, Toast.LENGTH_LONG).show()
                    }

                    return false
                }
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
