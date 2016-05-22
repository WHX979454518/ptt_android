package com.xianzhitech.ptt.ui.widget.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextPaint

class TextDrawable(val text: String, val tintColor: Int, textColor : Int = Color.WHITE) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()

    init {
        paint.color = tintColor
        paint.style = Paint.Style.FILL
        textPaint.color = textColor
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onBoundsChange(bounds: Rect) {
        textPaint.textSize = bounds.height() * 0.5f
        textPaint.getTextBounds(text, 0, text.length, textBounds)
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()

        if (width <= 0 || height <= 0) {
            return
        }

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawRect(bounds, paint)
        canvas.drawText(text, cx, cy - textBounds.exactCenterY(), textPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) { }
}