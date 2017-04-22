package com.xianzhitech.ptt.ui.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.isEmpty
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ui.base.BaseActivity


class TextInputActivity : BaseActivity() {
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_text_input)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editText = findView<EditText>(R.id.textInput_text)
        editText.hint = intent.getStringExtra(EXTRA_HINT)
        editText.setText(intent.getStringExtra(EXTRA_INITIAL_TEXT))

        title = intent.getCharSequenceExtra(EXTRA_TITLE)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.text_input, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.textInput_done) {
            if (intent.getBooleanExtra(EXTRA_REQUIRED, false) && editText.isEmpty()) {
                editText.error = R.string.field_required.toFormattedString(this)
                return false
            } else {
                setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_TEXT, editText.text.toString()))
                finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_HINT = "extra_hint"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_INITIAL_TEXT = "extra_initial_Text"
        const val EXTRA_REQUIRED = "extra_required"

        const val RESULT_EXTRA_TEXT = "result_extra_text"

        fun build(context: Context,
                  title: CharSequence,
                  hintText : CharSequence? = null,
                  initialText: CharSequence? = null,
                  required: Boolean = true): Intent {
            return Intent(context, TextInputActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_HINT, hintText)
                    .putExtra(EXTRA_INITIAL_TEXT, initialText)
                    .putExtra(EXTRA_REQUIRED, required)
        }
    }
}