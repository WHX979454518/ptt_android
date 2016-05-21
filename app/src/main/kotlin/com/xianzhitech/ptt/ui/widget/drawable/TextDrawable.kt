package com.xianzhitech.ptt.ui.widget.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextPaint
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toDimen

class TextDrawable(context: Context, private val text: String, private val tintColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()
    private var selected = false

    init {
        paint.color = tintColor
        paint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = Color.YELLOW
        strokePaint.strokeWidth = R.dimen.member_highlight_stroke.toDimen(context)
    }

    override fun onBoundsChange(bounds: Rect?) {
        bounds?.apply {
            textPaint.textSize = height().toFloat() * 0.5f
            textPaint.getTextBounds(text, 0, text.length, textBounds)
        }
        invalidateSelf()
    }

    override fun isStateful() = true

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()

        if (width <= 0 || height <= 0) {
            return
        }

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawRect(bounds, paint)

        if (selected) {
            canvas.drawRect(bounds, strokePaint)
        }

        canvas.drawText(text, cx, cy - textBounds.exactCenterY(), textPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }
}