package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        val bgColor = if (isDarkMode) DARK_BG else LIGHT_BG
        setBackgroundColor(bgColor)
    }

    fun setComposingText(text: String) {
        composingText = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (composingText.isNotEmpty()) {
            textPaint.color = if (isDarkMode) 0xFFB8C0C8.toInt() else 0xFF69727D.toInt()
            val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(composingText, 12f * resources.displayMetrics.density, baseline, textPaint)
        }
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    companion object {
        private const val DARK_BG = 0xFF20262C.toInt()
        private const val LIGHT_BG = 0xFFF7F8FA.toInt()
    }
}