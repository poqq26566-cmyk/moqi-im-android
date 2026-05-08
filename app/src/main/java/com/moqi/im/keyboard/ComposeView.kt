package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class ComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var composingText: String = ""

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14f)
        textAlign = Paint.Align.LEFT
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        setBackgroundColor(0x00000000)
        visibility = GONE
    }

    fun setComposingText(text: String) {
        composingText = text
        visibility = if (text.isEmpty()) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (composingText.isNotEmpty()) {
            val horizontalPadding = 10f * resources.displayMetrics.density
            val left = 8f * resources.displayMetrics.density
            val textWidth = textPaint.measureText(composingText)
            val rect = RectF(
                left,
                2f * resources.displayMetrics.density,
                left + textWidth + horizontalPadding * 2,
                height - 2f * resources.displayMetrics.density
            )
            bubblePaint.color = if (isDarkMode) 0xAA11161A.toInt() else 0xAA20262C.toInt()
            textPaint.color = 0xFFF2F4F6.toInt()
            canvas.drawRoundRect(rect, 6f * resources.displayMetrics.density, 6f * resources.displayMetrics.density, bubblePaint)
            val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(composingText, rect.left + horizontalPadding, baseline, textPaint)
        }
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )
}