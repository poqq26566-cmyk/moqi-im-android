package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.moqi.im.cloudclipboard.WebDavClipEntry
import com.moqi.im.theme.ThemePalette
import java.text.DateFormat
import java.util.Date

class CloudClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Callback {
        fun onBack()
        fun onRefresh()
        fun onClipSelected(name: String)
    }

    var callback: Callback? = null

    private val density = resources.displayMetrics.density
    private val scrollView = ScrollView(context)
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }
    private var loading = false

    init {
        applyThemeBackground()
        addView(createHeader(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        scrollView.addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        addView(
            scrollView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = dp(48)
            }
        )
        showLoading()
    }

    fun setLoading(loading: Boolean) {
        this.loading = loading
        if (loading) {
            showLoading()
        }
    }

    fun render(entries: List<WebDavClipEntry>, errorMessage: String? = null) {
        loading = false
        applyThemeBackground()
        content.removeAllViews()
        if (!errorMessage.isNullOrBlank()) {
            content.addView(createHint(errorMessage))
            return
        }
        if (entries.isEmpty()) {
            content.addView(createHint("暂无云剪贴板条目\n复制文本后会自动上传（需开启云剪贴板）"))
            return
        }
        entries.forEach { entry ->
            content.addView(createClipItem(entry))
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(createHint("正在加载…"))
    }

    private fun createHeader(): View {
        val theme = ThemePalette.current(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            setBackgroundColor(theme.candidateBackgroundColor)
        }
        row.addView(
            TextView(context).apply {
                text = "←"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(theme.textColor)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { callback?.onBack() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        row.addView(
            TextView(context).apply {
                text = "云剪贴板"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.textColor)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            TextView(context).apply {
                text = "刷新"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0xFF42A5F5.toInt())
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    if (!loading) callback?.onRefresh()
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return row
    }

    private fun createHint(message: String): View {
        val theme = ThemePalette.current(context)
        return TextView(context).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(adjustAlpha(theme.textColor, 0.55f))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
    }

    private fun createClipItem(entry: WebDavClipEntry): View {
        val theme = ThemePalette.current(context)
        val timeLabel = if (entry.lastModified > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(entry.lastModified))
        } else {
            ""
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val vertical = dp(10)
            setPadding(dp(12), vertical, dp(12), vertical)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
            background = roundedBackground(
                fillColor = if (isDarkMode()) 0xFF2A3138.toInt() else 0xFFF3F5F8.toInt(),
                strokeColor = if (isDarkMode()) 0xFF3E4852.toInt() else 0xFFD7DCE2.toInt()
            )
            isEnabled = !loading
            setOnClickListener {
                if (!loading) callback?.onClipSelected(entry.name)
            }
            addView(
                TextView(context).apply {
                    text = entry.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(theme.textColor)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
            if (timeLabel.isNotBlank()) {
                addView(
                    TextView(context).apply {
                        text = timeLabel
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(adjustAlpha(theme.textColor, 0.5f))
                        setPadding(0, dp(4), 0, 0)
                    }
                )
            }
        }
    }

    private fun applyThemeBackground() {
        setBackgroundColor(ThemePalette.current(context).candidateBackgroundColor)
    }

    private fun isDarkMode(): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun roundedBackground(fillColor: Int, strokeColor: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return (color and 0x00FFFFFF) or ((alpha.toInt().coerceIn(0, 255)) shl 24)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
