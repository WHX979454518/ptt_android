package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.xianzhitech.ptt.R

class CircleShadowView : View {
    var circleRadius: Int = 0
        set(value) {
            if (field != value) {
                field = value
                path = null
                invalidate()
            }
        }
    var shadowColor: Int = Color.argb(120, 0, 0, 0)
        set(value) {
            if (field != value) {
                field = value
                applyShadowColor(paint)
                invalidate()
            }
        }

    var color: Int
        get() = paint.color
        set(value) {
            if (paint.color != value) {
                paint.color = value
                invalidate()
            }
        }

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        applyShadowColor(this)
    }

    private var path: Path? = null
    private val shadowSize = resources.getDimensionPixelSize(R.dimen.shadow_size)
    private val circleTopHeight = resources.getDimensionPixelSize(R.dimen.circle_top_height)

    constructor(context: Context?) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun init(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircleShadowView, defStyleAttr, defStyleRes)
        circleRadius = typedArray.getDimensionPixelSize(R.styleable.CircleShadowView_circleRadius, 0)
        shadowColor = typedArray.getColor(R.styleable.CircleShadowView_shadowColor, shadowColor)
        color = typedArray.getColor(R.styleable.CircleShadowView_fgColor, color)
        typedArray.recycle()
    }


    private fun applyShadowColor(paint: Paint) {
        if (isInEditMode.not()) {
            paint.setShadowLayer(15f, 0f, shadowSize.toFloat(), shadowColor)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val calculatedSize: Int = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.AT_MOST -> Math.min(circleRadius + circleTopHeight, heightSize)
            MeasureSpec.EXACTLY -> heightSize
            else -> circleRadius + circleTopHeight
        }

        setMeasuredDimension(measuredWidth, calculatedSize)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path = null
    }

    override fun onDraw(canvas: Canvas) {
        var width = this.width
        var height = this.height - shadowSize

        if (width <= 0 || height <= 0) {
            return
        }

        if (path == null) {
            path = Path().apply {
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)

                if (circleRadius > 0 && isInEditMode.not()) {
                    var circlePath = Path().apply {
                        addCircle(width / 2f, height.toFloat(), circleRadius.toFloat(), Path.Direction.CW)
                    }

                    if (Build.VERSION.SDK_INT >= 19) {
                        op(circlePath, Path.Op.DIFFERENCE)
                    }
                }
            }
        }

        canvas.drawPath(path, paint)
    }
}