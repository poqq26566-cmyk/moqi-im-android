package com.moqi.im.settings

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.moqi.im.R

class InputTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_test)

        val editText = findViewById<EditText>(R.id.input_test_edit)
        editText.requestFocus()
        editText.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
        }
    }
}
