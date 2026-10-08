package com.omrscanner.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.omrscanner.core.AnswerKeyCodec
import com.omrscanner.core.SheetSpec

/** Lets the teacher tap in the correct answers, paste them, or scan a filled-in sheet. */
class AnswerKeyActivity : Activity() {
    private lateinit var store: Store
    private lateinit var settings: ExamSettings
    private lateinit var key: MutableList<Int?>
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "Answer key"
        val page = scrollingPage()

        val top = card()
        status = top.add(text("", 15f), block(10)) as TextView
        top.add(text("Tap the correct option for each question, or scan a sheet filled in with the right answers.", 14f, Ui.MUTED), block(12))
        val scanRow = horizontal()
        scanRow.add(button("Scan key sheet") { ImageSources.openCamera(this) }, weighted().apply { marginEnd = dp(8) })
        scanRow.add(button("From picture", ButtonStyle.SECONDARY) { ImageSources.openGallery(this) }, weighted())
        top.add(scanRow, block(8))
        val editRow = horizontal()
        editRow.add(button("Type / paste", ButtonStyle.SECONDARY) { pasteKey() }, weighted().apply { marginEnd = dp(8) })
        editRow.add(button("Clear all", ButtonStyle.DANGER) { confirmClear() }, weighted())
        top.add(editRow, block(0))
        page.add(top, block(16))

        rows = card().apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        page.add(rows, block(0))
    }

    override fun onResume() {
        super.onResume()
        settings = store.settings
        key = store.key.toMutableList()
        buildRows()
    }

    private fun buildRows() {
        rows.removeAllViews()
        for (q in 0 until settings.questionCount) {
            val row = horizontal().apply { setPadding(0, dp(4), 0, dp(4)) }
            row.add(text("${q + 1}", 15f, Ui.MUTED, bold = true).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL },
                LinearLayout.LayoutParams(dp(36), dp(40)).apply { marginEnd = dp(12) })
            for (o in 0 until settings.optionCount) {
                val bubble = text(settings.label(o), 15f, bold = true).apply { gravity = Gravity.CENTER }
                paintBubble(bubble, key[q] == o)
                bubble.setOnClickListener {
                    key[q] = if (key[q] == o) null else o
                    store.key = key
                    for (i in 0 until settings.optionCount) paintBubble(row.getChildAt(i + 1) as TextView, key[q] == i)
                    updateStatus()
                }
                row.add(bubble, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
            }
            rows.add(row, block(0))
        }
        updateStatus()
    }

    private fun paintBubble(v: TextView, selected: Boolean) {
        v.background = rounded(if (selected) Ui.PRIMARY else Ui.CARD, dp(20).toFloat(), if (selected) Ui.PRIMARY else Ui.MUTED, dp(1.5f))
        v.setTextColor(if (selected) 0xFFFFFFFF.toInt() else Ui.MUTED)
    }

    private fun updateStatus() {
        val n = key.count { it != null }
        status.text = "$n of ${settings.questionCount} answers set" + if (n < settings.questionCount) " — questions without a key are not graded" else " ✓"
    }

    private fun pasteKey() {
        val input = EditText(this).apply {
            setText(AnswerKeyCodec.encode(key))
            hint = "e.g. ABDCA…"
            setSingleLine(false)
        }
        val box = vertical(20)
        box.add(text("One letter per question in order (A, B, C…). Use - to skip a question. Spaces and numbers are ignored.", 14f, Ui.MUTED), block(8))
        box.add(input, block(0))
        AlertDialog.Builder(this)
            .setTitle("Type or paste the key")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                // Accept Bangla option letters too (ক = A, খ = B, …).
                val cleaned = input.text.toString()
                    .map { ch -> SheetSpec.BANGLA_LABELS.indexOf(ch.toString()).let { i -> if (i >= 0) 'A' + i else ch } }
                    .filter { it.isLetter() || it == '-' }
                    .joinToString("")
                val decoded = AnswerKeyCodec.decode(cleaned, settings.optionCount)
                key = MutableList(settings.questionCount) { decoded.getOrNull(it) }
                store.key = key
                buildRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("Clear the whole answer key?")
            .setPositiveButton("Clear") { _, _ ->
                key = MutableList(settings.questionCount) { null }
                store.key = key
                buildRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = ImageSources.resultUri(this, requestCode, resultCode, data) ?: return
        startActivity(
            Intent(this, ScanActivity::class.java).setData(uri)
                .putExtra(ScanActivity.EXTRA_KEY_MODE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
