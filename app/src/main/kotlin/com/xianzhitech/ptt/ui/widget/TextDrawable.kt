package com.xianzhitech.ptt.ui.widget

import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint

/**
 * Created by fanchao on 30/12/15.
 */
class TextDrawable(private val text: String, private val tintColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()

    init {
        paint.color = tintColor
        paint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onBoundsChange(bounds: Rect?) {
        bounds?.apply {
            textPaint.textSize = height().toFloat() * 0.5f
            textPaint.getTextBounds(text, 0, text.length, textBounds)
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas?) {
        canvas?.apply {
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            drawCircle(halfWidth, halfHeight, Math.min(halfHeight, halfWidth), paint)
            drawText(text, halfWidth, halfHeight - textBounds.exactCenterY(), textPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }
}