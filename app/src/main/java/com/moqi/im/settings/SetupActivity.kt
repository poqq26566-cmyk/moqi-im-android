package com.moqi.im.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.moqi.im.R

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<Button>(R.id.btn_enable_ime).setOnClickListener {
            openInputMethodSettings()
        }

        findViewById<Button>(R.id.btn_select_ime).setOnClickListener {
            showInputMethodPicker()
        }

        findViewById<Button>(R.id.btn_input_test).setOnClickListener {
            startActivity(Intent(this, InputTestActivity::class.java))
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val enabled = isImeEnabled()
        val selected = isImeSelected()

        val statusText = findViewById<TextView>(R.id.tv_setup_status)
        val btnEnable = findViewById<Button>(R.id.btn_enable_ime)
        val btnSelect = findViewById<Button>(R.id.btn_select_ime)

        when {
            !enabled -> {
                statusText.text = getString(R.string.setup_step1)
                btnEnable.visibility = View.VISIBLE
                btnSelect.visibility = View.GONE
            }
            !selected -> {
                statusText.text = getString(R.string.setup_step2)
                btnEnable.visibility = View.GONE
                btnSelect.visibility = View.VISIBLE
            }
            else -> {
                statusText.text = getString(R.string.setup_done)
                btnEnable.visibility = View.GONE
                btnSelect.visibility = View.GONE
            }
        }
    }

    private fun isImeEnabled(): Boolean {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val enabledIMEs = inputMethodManager.enabledInputMethodList
        return enabledIMEs.any { it.packageName == packageName }
    }

    private fun isImeSelected(): Boolean {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return inputMethodManager.currentInputMethodInfo?.packageName == packageName
    }

    private fun openInputMethodSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun showInputMethodPicker() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }
}