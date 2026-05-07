package com.moqi.im.keyboard

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.moqi.im.engine.RimeMenuEntry
import com.moqi.im.engine.RimeSchemaEntry

class KeyboardMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    data class MenuState(
        val menuEntries: List<RimeMenuEntry> = emptyList(),
        val schemeSets: List<String> = emptyList(),
        val currentSchemeSet: String = "",
        val schemas: List<RimeSchemaEntry> = emptyList(),
        val currentSchemaId: String = ""
    )

    interface Callback {
        fun onBack()
        fun onCommand(commandId: Int)
        fun onSchemeSet(name: String)
        fun onSchema(schemaId: String)
        fun onInputMethodPicker()
        fun onVoiceInput()
        fun onOpenSettings()
        fun onDownloadScheme(url: String, schemeSetName: String)
    }

    var callback: Callback? = null

    private val density = resources.displayMetrics.density
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(16))
    }
    private val downloadUrl = EditText(context).apply {
        hint = "方案集 ZIP URL"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setSingleLine(true)
    }
    private val downloadName = EditText(context).apply {
        hint = "方案集名称"
        inputType = InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
    }

    init {
        setBackgroundColor(Color.rgb(240, 240, 245))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        render(MenuState())
    }

    fun render(state: MenuState) {
        content.removeAllViews()
        addHeader()
        addSection("常用")
        addGrid(
            listOf(
                Action("输入法设置") { callback?.onOpenSettings() },
                Action("切换输入法") { callback?.onInputMethodPicker() },
                Action("长按空格语音") { callback?.onBack() },
                Action("键盘") { callback?.onBack() }
            )
        )

        val commandItems = state.menuEntries.filter { it.commandId != 0 && it.text.isNotBlank() }
        val statusItems = commandItems.filter { it.group.isBlank() }.take(12)
        if (statusItems.isNotEmpty()) {
            addSection("输入状态")
            addGrid(statusItems.map { entry ->
                Action(entry.displayText()) { callback?.onCommand(entry.commandId) }
            })
        }

        if (state.schemeSets.isNotEmpty()) {
            addSection("方案集")
            addGrid(state.schemeSets.map { name ->
                Action(if (name == state.currentSchemeSet) "✓ $name" else name) {
                    callback?.onSchemeSet(name)
                }
            })
        }

        if (state.schemas.isNotEmpty()) {
            addSection("输入方案")
            addGrid(state.schemas.map { schema ->
                val selected = schema.id == state.currentSchemaId || schema.selected
                Action(if (selected) "✓ ${schema.name}" else schema.name) {
                    callback?.onSchema(schema.id)
                }
            })
        }

        val configItems = commandItems.filter {
            it.text.contains("刷新") || it.text.contains("更新") || it.text.contains("配置")
        }.distinctBy { it.commandId }
        if (configItems.isNotEmpty()) {
            addSection("配置")
            addGrid(configItems.map { entry ->
                Action(entry.displayText()) { callback?.onCommand(entry.commandId) }
            })
        }

        addDownloadSection()
    }

    private fun addHeader() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(menuButton("←") { callback?.onBack() }, LinearLayout.LayoutParams(dp(54), dp(44)))
        row.addView(TextView(context).apply {
            text = "墨奇菜单"
            textSize = 18f
            setTextColor(Color.rgb(26, 26, 46))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(menuButton("设置") { callback?.onOpenSettings() }, LinearLayout.LayoutParams(dp(72), dp(44)))
        content.addView(row)
    }

    private fun addSection(title: String) {
        content.addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(Color.rgb(96, 96, 128))
            setPadding(0, dp(12), 0, dp(6))
        })
    }

    private fun addGrid(actions: List<Action>) {
        val grid = GridLayout(context).apply {
            columnCount = 4
            useDefaultMargins = true
        }
        actions.forEach { action ->
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(64)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(menuButton(action.label, action.onClick), lp)
        }
        content.addView(grid, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addDownloadSection() {
        addSection("下载新方案集")
        content.addView(downloadUrl, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(downloadName, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(menuButton("下载并切换") {
            callback?.onDownloadScheme(downloadUrl.text.toString(), downloadName.text.toString())
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun menuButton(textValue: String, onClick: View.OnClickListener): Button {
        return Button(context).apply {
            text = textValue
            textSize = 14f
            isAllCaps = false
            setOnClickListener(onClick)
        }
    }

    private fun RimeMenuEntry.displayText(): String {
        val prefix = if (checked) "✓ " else ""
        return prefix + text.replace("(&I)", "").replace("(&P)", "").replace("(&R)", "")
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private data class Action(
        val label: String,
        val onClick: View.OnClickListener
    )
}
