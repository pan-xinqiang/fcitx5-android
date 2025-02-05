package org.fcitx.fcitx5.android.ui.main

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlinx.coroutines.*
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.databinding.ActivityClipboardEditBinding
import org.fcitx.fcitx5.android.utils.str
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager

class ClipboardEditActivity : Activity() {

    private val scope: CoroutineScope = MainScope()

    private lateinit var editText: EditText

    private var entryId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding = ActivityClipboardEditBinding.inflate(layoutInflater).apply {
            editText = clipboardEditText
            clipboardEditCancel.setOnClickListener { finish() }
            clipboardEditOk.setOnClickListener { finishEditing() }
            clipboardEditCopy.setOnClickListener { finishEditing(copy = true) }
        }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        processIntent(intent)
    }

    private fun finishEditing(copy: Boolean = false) {
        val str = editText.str
        scope.launch(NonCancellable) {
            ClipboardManager.updateText(entryId, str)
            if (copy) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", str))
            }
        }
        finish()
    }

    private fun setEntry(entry: ClipboardEntry) {
        entryId = entry.id
        editText.setText(entry.text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        scope.launch {
            intent.extras?.run {
                if (getBoolean(LAST_ENTRY)) {
                    ClipboardManager.lastEntry
                } else {
                    ClipboardManager.get(getInt(ENTRY_ID))
                }
            }?.let { setEntry(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val ENTRY_ID = "id"
        const val LAST_ENTRY = "last_entry"
    }
}
