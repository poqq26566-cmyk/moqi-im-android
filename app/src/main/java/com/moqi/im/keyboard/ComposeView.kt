package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ComposeView @Suppress("unused") constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var composingText: String = ""

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        textAlign = Paint.Align.LEFT
    }

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        val bgColor = if (isDarkMode) 0xFF1A1A2E.toInt() else 0xFFFAFAFC.toInt()
        setBackgroundColor(bgColor)
        textPaint.color = if (isDarkMode) 0xFFE0E0E8.toInt() else 0xFF1A1A2E.toInt()
    }

    fun setComposingText(text: String) {
        composingText = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (composingText.isNotEmpty()) {
            val padding = 12f * resources.displayMetrics.density
            canvas.drawText(composingText, padding, height / 2f - textPaint.fontMetrics.ascent / 2 + textPaint.descent / 3, textPaint)
        }
    }
}