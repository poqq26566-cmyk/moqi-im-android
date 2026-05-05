package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.moqi.im.R

class CandidateView @Suppress("unused") constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var candidates: List<String> = emptyList()
    private var itemRects: List<RectF> = emptyList()
    private var pressedIndex: Int = -1

    private var onCandidateSelected: ((String) -> Unit)? = null

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        val bgColor = if (isDarkMode) 0xFF1A1A2E.toInt() else 0xFFF0F0F5.toInt()
        setBackgroundColor(bgColor)
    }

    fun setCandidates(candidates: List<String>) {
        this.candidates = candidates
        pressedIndex = -1
        requestLayout()
        invalidate()
    }

    fun getFirstCandidate(): String? = candidates.firstOrNull()

    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color = if (isDarkMode) 0xFFE0E0E8.toInt() else 0xFF1A1A2E.toInt()

        for ((i, rect) in itemRects.withIndex()) {
            if (i >= candidates.size) break
            val text = candidates[i]
            val textHeight = paint.fontMetrics.let { it.descent - it.ascent }
            val isSelected = i == pressedIndex

            if (isSelected) {
                val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                hlPaint.color = if (isDarkMode) 0xFF2A2A3E.toInt() else 0xFFE0E0E8.toInt()
                canvas.drawRoundRect(rect, 4f * resources.displayMetrics.density, 4f * resources.displayMetrics.density, hlPaint)
            }

            canvas.drawText(text, rect.centerX(), rect.centerY() + textHeight / 3, paint)
        }

        if (candidates.isEmpty()) {
            paint.color = if (isDarkMode) 0xFF606080.toInt() else 0xFF9090AA.toInt()
            paint.textSize = 28f
            val textHeight = paint.fontMetrics.let { it.descent - it.ascent }
            canvas.drawText("墨奇输入法", width / 2f, height / 2f + textHeight / 3, paint)
            paint.textSize = 36f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val h = resources.getDimension(R.dimen.candidate_height)
        calculateItemRects(width)
        setMeasuredDimension(width, h.toInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = findItemAt(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val idx = findItemAt(event.x)
                if (idx in candidates.indices && idx == pressedIndex) {
                    onCandidateSelected?.invoke(candidates[idx])
                }
                pressedIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun calculateItemRects(totalWidth: Int) {
        val padding = 8f * resources.displayMetrics.density
        val itemWidth = (totalWidth - padding * 2) / maxOf(candidates.size, 1).coerceAtMost(5)
        itemRects = candidates.indices.map { i ->
            RectF(padding + i * itemWidth, 0f, padding + (i + 1) * itemWidth, height.toFloat())
        }
    }

    private fun findItemAt(x: Float): Int {
        itemRects.forEachIndexed { i, rect ->
            if (rect.contains(x, rect.centerY())) return i
        }
        return -1
    }
}