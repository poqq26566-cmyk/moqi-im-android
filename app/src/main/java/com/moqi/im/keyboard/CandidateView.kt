package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.moqi.im.R
import com.moqi.im.engine.CandidateEntry
import kotlin.math.abs

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(19f)
        textAlign = Paint.Align.LEFT
    }
    private val commentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(17f)
        textAlign = Paint.Align.LEFT
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var candidates: List<CandidateEntry> = emptyList()
    private var itemRects: List<RectF> = emptyList()
    private var controlRects: List<RectF> = emptyList()
    private var moreButtonRect = RectF()
    private var pressedIndex: Int = -1
    private var pressedControl: Int = -1
    private var moreButtonPressed = false
    private var expanded = false
    private var scrollOffset = 0f
    private var maxScrollOffset = 0f
    private var downX = 0f
    private var lastX = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var onCandidateSelected: ((String) -> Unit)? = null
    private var onCandidateIndexSelected: ((Int) -> Unit)? = null
    private var onExpandedChanged: ((Boolean) -> Unit)? = null
    private var onCandidatePageChange: ((Boolean) -> Unit)? = null

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
        pressedControl = -1
        moreButtonPressed = false
        scrollOffset = 0f
        if (candidates.isEmpty()) {
            expanded = false
            onExpandedChanged?.invoke(false)
        }
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

    fun setOnExpandedChangedListener(listener: (Boolean) -> Unit) {
        onExpandedChanged = listener
    }

    fun setOnCandidatePageChangeListener(listener: (Boolean) -> Unit) {
        onCandidatePageChange = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.color = if (isDarkMode) 0xFFF3F5F7.toInt() else 0xFF20242A.toInt()
        commentPaint.color = if (isDarkMode) 0xFF9CA3AA.toInt() else 0xFF69727D.toInt()
        dividerPaint.color = if (isDarkMode) 0xFF3A4148.toInt() else 0xFFD7DCE2.toInt()
        highlightPaint.color = if (isDarkMode) 0xFF303942.toInt() else 0xFFE5E9EF.toInt()
        arrowPaint.color = if (isDarkMode) 0xFFB8C0C8.toInt() else 0xFF69727D.toInt()

        canvas.save()
        canvas.clipRect(0f, 0f, moreButtonRect.left, height.toFloat())
        if (!expanded) {
            canvas.translate(-scrollOffset, 0f)
        }
        for ((i, rect) in itemRects.withIndex()) {
            if (i >= candidates.size) break
            if (!expanded && (rect.right < scrollOffset || rect.left > scrollOffset + moreButtonRect.left)) continue

            val candidate = candidates[i]
            val isSelected = i == pressedIndex

            canvas.save()
            canvas.clipRect(rect.left + dp(1f), rect.top, rect.right - dp(1f), rect.bottom)
            if (isSelected) {
                canvas.drawRoundRect(rect, dp(6f), dp(6f), highlightPaint)
            }

            val textX = rect.left + dp(12f)
            val textBaseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(candidate.text, textX, textBaseline, textPaint)
            if (candidate.comment.isNotBlank()) {
                val commentX = textX + textPaint.measureText(candidate.text) + dp(7f)
                canvas.drawText(candidate.comment, commentX, textBaseline, commentPaint)
            }
            canvas.restore()
            if (i < itemRects.lastIndex) {
                canvas.drawLine(rect.right, dp(8f), rect.right, height - dp(8f), dividerPaint)
            }
        }
        canvas.restore()

        if (candidates.isNotEmpty()) {
            if (moreButtonPressed) {
                canvas.drawRoundRect(moreButtonRect, dp(6f), dp(6f), highlightPaint)
            }
            canvas.drawLine(moreButtonRect.left, dp(8f), moreButtonRect.left, height - dp(8f), dividerPaint)
            if (expanded) {
                drawExpandedControls(canvas)
            } else {
                drawMoreArrow(canvas)
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
                downX = event.x
                lastX = event.x
                isDragging = false
                pressedControl = findControlAt(event.x, event.y)
                moreButtonPressed = (pressedControl >= 0 || moreButtonRect.contains(event.x, event.y)) && candidates.isNotEmpty()
                pressedIndex = if (moreButtonPressed) -1 else findItemAt(touchContentX(event.x), event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (moreButtonPressed) {
                    if (pressedControl >= 0 && findControlAt(event.x, event.y) != pressedControl) {
                        moreButtonPressed = false
                        pressedControl = -1
                        invalidate()
                    }
                    return true
                }
                if (expanded) {
                    return true
                }
                val dx = lastX - event.x
                if (!isDragging && abs(event.x - downX) > touchSlop) {
                    isDragging = true
                    pressedIndex = -1
                }
                if (isDragging) {
                    scrollOffset = (scrollOffset + dx).coerceIn(0f, maxScrollOffset)
                    invalidate()
                }
                lastX = event.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moreButtonPressed) {
                    val control = findControlAt(event.x, event.y)
                    if (expanded && control == pressedControl) {
                        when (control) {
                            CONTROL_PREV -> onCandidatePageChange?.invoke(true)
                            CONTROL_NEXT -> onCandidatePageChange?.invoke(false)
                            CONTROL_COLLAPSE -> setExpanded(false)
                        }
                    } else if (!expanded && moreButtonRect.contains(event.x, event.y)) {
                        setExpanded(true)
                    }
                    moreButtonPressed = false
                    pressedControl = -1
                    invalidate()
                    return true
                }
                val idx = findItemAt(touchContentX(event.x), event.y)
                if (!isDragging && idx in candidates.indices && idx == pressedIndex) {
                    onCandidateIndexSelected?.invoke(idx)
                    onCandidateSelected?.invoke(candidates[idx].text)
                }
                pressedIndex = -1
                isDragging = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                pressedControl = -1
                moreButtonPressed = false
                isDragging = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun calculateItemRects(totalWidth: Int, totalHeight: Float = height.toFloat()) {
        val padding = dp(4f)
        val moreButtonWidth = if (candidates.isEmpty()) 0f else dp(48f)
        val contentWidth = (totalWidth - moreButtonWidth).coerceAtLeast(0f)
        moreButtonRect = RectF(contentWidth, 0f, totalWidth.toFloat(), totalHeight)
        if (expanded) {
            val columns = 3
            val itemGap = dp(4f)
            val rowHeight = dp(42f)
            val itemWidth = ((contentWidth - padding * 2 - itemGap * (columns - 1)) / columns).coerceAtLeast(dp(62f))
            itemRects = candidates.mapIndexed { index, _ ->
                val row = index / columns
                val col = index % columns
                val left = padding + col * (itemWidth + itemGap)
                val top = padding + row * rowHeight
                RectF(left, top, left + itemWidth, top + rowHeight)
            }
            maxScrollOffset = 0f
            scrollOffset = 0f
        } else {
            var x = padding
            itemRects = candidates.map { candidate ->
                val desiredWidth = dp(24f) +
                    textPaint.measureText(candidate.text) +
                    if (candidate.comment.isBlank()) 0f else dp(8f) + commentPaint.measureText(candidate.comment)
                val itemWidth = desiredWidth.coerceAtLeast(dp(62f))
                RectF(x, 0f, x + itemWidth, totalHeight).also {
                    x += itemWidth
                }
            }
            maxScrollOffset = (x + padding - contentWidth).coerceAtLeast(0f)
            scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset)
        }
        calculateControlRects()
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        scrollOffset = 0f
        onExpandedChanged?.invoke(expanded)
        requestLayout()
        invalidate()
    }

    private fun calculateControlRects() {
        if (!expanded || moreButtonRect.isEmpty) {
            controlRects = emptyList()
            return
        }
        val buttonHeight = moreButtonRect.height() / 3f
        controlRects = listOf(
            RectF(moreButtonRect.left, moreButtonRect.top, moreButtonRect.right, moreButtonRect.top + buttonHeight),
            RectF(moreButtonRect.left, moreButtonRect.top + buttonHeight, moreButtonRect.right, moreButtonRect.top + buttonHeight * 2f),
            RectF(moreButtonRect.left, moreButtonRect.top + buttonHeight * 2f, moreButtonRect.right, moreButtonRect.bottom)
        )
    }

    private fun drawMoreArrow(canvas: Canvas) {
        val cx = moreButtonRect.centerX()
        val cy = moreButtonRect.centerY() + dp(1f)
        val halfWidth = dp(8f)
        val halfHeight = dp(5f)
        val path = Path().apply {
            moveTo(cx - halfWidth, cy - halfHeight)
            lineTo(cx + halfWidth, cy - halfHeight)
            lineTo(cx, cy + halfHeight)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawExpandedControls(canvas: Canvas) {
        val labels = listOf("⌃", "⌄", "×")
        val baselineOffset = -(commentPaint.descent() + commentPaint.ascent()) / 2f
        commentPaint.textAlign = Paint.Align.CENTER
        commentPaint.color = if (isDarkMode) 0xFFB8C0C8.toInt() else 0xFF69727D.toInt()
        for ((index, rect) in controlRects.withIndex()) {
            if (index == pressedControl && moreButtonPressed) {
                canvas.drawRoundRect(rect, dp(6f), dp(6f), highlightPaint)
            }
            if (index > 0) {
                canvas.drawLine(rect.left + dp(8f), rect.top, rect.right - dp(8f), rect.top, dividerPaint)
            }
            canvas.drawText(labels[index], rect.centerX(), rect.centerY() + baselineOffset, commentPaint)
        }
        commentPaint.textAlign = Paint.Align.LEFT
    }

    private fun findItemAt(x: Float, y: Float): Int {
        itemRects.forEachIndexed { i, rect ->
            val hitY = if (expanded) y else rect.centerY()
            if (rect.contains(x, hitY)) return i
        }
        return -1
    }

    private fun findControlAt(x: Float, y: Float): Int {
        if (!expanded) return -1
        controlRects.forEachIndexed { index, rect ->
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    private fun touchContentX(x: Float): Float = if (expanded) x else x + scrollOffset

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    companion object {
        private const val DARK_BG = 0xFF20262C.toInt()
        private const val LIGHT_BG = 0xFFF7F8FA.toInt()
        private const val CONTROL_PREV = 0
        private const val CONTROL_NEXT = 1
        private const val CONTROL_COLLAPSE = 2
    }
}