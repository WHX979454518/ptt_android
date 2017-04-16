package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.support.v4.view.NestedScrollingChild
import android.support.v4.view.NestedScrollingChildHelper
import android.support.v4.widget.SwipeRefreshLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.xianzhitech.ptt.R

class SideNavigationView @JvmOverloads constructor(context : Context,
                                                   attrs : AttributeSet? = null,
                                                   defStyleAttr : Int = 0) : View(context, attrs, defStyleAttr) {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private var cellHeight = 0f

    var onNavigateListener : OnNavigationListener? = null

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SideNavigationView, defStyleAttr, 0)
        textPaint.color = typedArray.getColor(R.styleable.SideNavigationView_android_textColor, Color.BLACK)
        typedArray.recycle()

        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun getHitRect(outRect: Rect) {
        super.getHitRect(outRect)
        outRect.inset(-resources.getDimensionPixelSize(R.dimen.unit_five), 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        cellHeight = (h - paddingTop - paddingBottom).toFloat() / CHARS.size
        textPaint.textSize = cellHeight * 0.7f
    }

    private fun onMoveTo(y: Float) {
        if (cellHeight > 0 && onNavigateListener != null) {
            val index = Math.max(0, Math.min(CHARS.size - 1, (CHARS.size * y / (height - paddingTop - paddingBottom)).toInt()))
            onNavigateListener!!.onNavigateTo(CHARS[index])
        }
    }

    private fun findSRL() : SwipeRefreshLayout? {
        var walk = parent
        while (walk != null) {
            if (walk is SwipeRefreshLayout) {
                return walk
            }

            walk = walk.parent
        }

        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                isPressed = false
                onMoveTo(event.y)
                onNavigateListener?.onNavigateCancel()
                findSRL()?.isEnabled = true
            }

            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                findSRL()?.isEnabled = false
                onMoveTo(event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                onMoveTo(event.y)
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var y = paddingTop.toFloat()
        val halfWidth = width / 2f
        val halfCellHeight = cellHeight / 2f + textPaint.textSize / 2f
        for (i in 0..CHARS.size - 1) {
            canvas.drawText(CHARS[i], halfWidth, y + halfCellHeight, textPaint)
            y += cellHeight
        }
    }

    interface OnNavigationListener {
        fun onNavigateTo(c: String)
        fun onNavigateCancel()
    }

    companion object {
        private val CHARS = Array(27, {
            if (it == 0) {
                "#"
            }
            else {
                ('A' + it - 1).toString()
            }
        })
    }
}