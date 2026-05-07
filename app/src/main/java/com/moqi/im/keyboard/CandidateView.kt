package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.moqi.im.R
import com.moqi.im.engine.CandidateEntry

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(23f)
        textAlign = Paint.Align.LEFT
    }
    private val commentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(16f)
        textAlign = Paint.Align.LEFT
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var candidates: List<CandidateEntry> = emptyList()
    private var itemRects: List<RectF> = emptyList()
    private var pressedIndex: Int = -1

    private var onCandidateSelected: ((String) -> Unit)? = null
    private var onCandidateIndexSelected: ((Int) -> Unit)? = null

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        setBackgroundColor(if (isDarkMode) DARK_BG else LIGHT_BG)
    }

    fun setCandidates(candidates: List<String>) {
        setCandidateEntries(candidates.map { CandidateEntry(it, "") })
    }

    fun setCandidateEntries(candidates: List<CandidateEntry>) {
        this.candidates = candidates
        pressedIndex = -1
        requestLayout()
        invalidate()
    }

    fun getFirstCandidate(): String? = candidates.firstOrNull()?.text

    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    fun setOnCandidateIndexSelectedListener(listener: (Int) -> Unit) {
        onCandidateIndexSelected = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.color = if (isDarkMode) 0xFFF3F5F7.toInt() else 0xFF20242A.toInt()
        commentPaint.color = if (isDarkMode) 0xFF9CA3AA.toInt() else 0xFF69727D.toInt()
        dividerPaint.color = if (isDarkMode) 0xFF3A4148.toInt() else 0xFFD7DCE2.toInt()
        highlightPaint.color = if (isDarkMode) 0xFF303942.toInt() else 0xFFE5E9EF.toInt()

        for ((i, rect) in itemRects.withIndex()) {
            if (i >= candidates.size) break
            val candidate = candidates[i]
            val isSelected = i == pressedIndex

            if (isSelected) {
                canvas.drawRoundRect(rect, dp(6f), dp(6f), highlightPaint)
            }

            val textX = rect.left + dp(14f)
            val textBaseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(candidate.text, textX, textBaseline, textPaint)
            if (candidate.comment.isNotBlank()) {
                val commentX = textX + textPaint.measureText(candidate.text) + dp(8f)
                canvas.drawText(candidate.comment, commentX, textBaseline, commentPaint)
            }
            if (i < itemRects.lastIndex) {
                canvas.drawLine(rect.right, dp(8f), rect.right, height - dp(8f), dividerPaint)
            }
        }

        if (candidates.isEmpty()) {
            commentPaint.color = if (isDarkMode) 0xFF858C94.toInt() else 0xFF8A929C.toInt()
            commentPaint.textAlign = Paint.Align.CENTER
            val baseline = height / 2f - (commentPaint.descent() + commentPaint.ascent()) / 2f
            canvas.drawText("墨奇输入法", width / 2f, baseline, commentPaint)
            commentPaint.textAlign = Paint.Align.LEFT
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val h = resources.getDimension(R.dimen.candidate_height)
        calculateItemRects(width, h)
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
                    onCandidateIndexSelected?.invoke(idx)
                    onCandidateSelected?.invoke(candidates[idx].text)
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

    private fun calculateItemRects(totalWidth: Int, totalHeight: Float = height.toFloat()) {
        val padding = dp(4f)
        var x = padding
        itemRects = candidates.map { candidate ->
            val desiredWidth = dp(28f) +
                textPaint.measureText(candidate.text) +
                if (candidate.comment.isBlank()) 0f else dp(10f) + commentPaint.measureText(candidate.comment)
            val itemWidth = desiredWidth.coerceIn(dp(72f), totalWidth * 0.42f)
            RectF(x, 0f, x + itemWidth, totalHeight).also {
                x += itemWidth
            }
        }
    }

    private fun findItemAt(x: Float): Int {
        itemRects.forEachIndexed { i, rect ->
            if (rect.contains(x, rect.centerY())) return i
        }
        return -1
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

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