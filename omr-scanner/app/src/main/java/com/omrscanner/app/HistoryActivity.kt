package com.omrscanner.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Results saved from the result screen, with CSV export for spreadsheets. */
class HistoryActivity : Activity() {
    private lateinit var store: Store
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "Saved results"
        val page = scrollingPage()
        val actions = horizontal()
        actions.add(button("Export CSV") { export() }, weighted().apply { marginEnd = dp(8) })
        actions.add(button("Clear all", ButtonStyle.DANGER) { confirmClear() }, weighted())
        page.add(actions, block(16))
        list = page.add(vertical(), block(0)) as LinearLayout
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val results = store.history().reversed()
        if (results.isEmpty()) {
            list.add(text("No saved results yet. After scanning a sheet, tap \"Save result\".", 15f, Ui.MUTED))
            return
        }
        val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        for (r in results) {
            val c = card()
            val top = horizontal()
            top.add(text("Roll ${r.roll.ifBlank { "?" }}", 17f, bold = true), weighted())
            val pct = if (r.maxScore > 0) 100 * r.score / r.maxScore else 0.0
            top.add(text("${fmt(r.score)}/${fmt(r.maxScore)}  (${String.format(Locale.US, "%.0f", pct)}%)", 17f, Ui.PRIMARY, bold = true))
            c.add(top, block(4))
            c.add(text("✓ ${r.correct}   ✗ ${r.wrong}   blank ${r.blank}   multiple ${r.multiple}", 14f, Ui.MUTED), block(2))
            c.add(text(listOf(r.exam, dates.format(Date(r.time))).filter { it.isNotBlank() }.joinToString(" • "), 13f, Ui.MUTED), block(0))
            list.add(c, block(10))
        }
    }

    private fun export() {
        val results = store.history()
        if (results.isEmpty()) { toast("Nothing to export yet"); return }
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val csv = StringBuilder("Time,Exam,Roll,Score,Max,Percent,Correct,Wrong,Blank,Multiple,Answers\n")
        for (r in results) {
            val pct = if (r.maxScore > 0) 100 * r.score / r.maxScore else 0.0
            csv.append(listOf(
                iso.format(Date(r.time)), r.exam, r.roll, fmt(r.score), fmt(r.maxScore), String.format(Locale.US, "%.1f", pct),
                r.correct, r.wrong, r.blank, r.multiple, r.answers,
            ).joinToString(",") { cell(it.toString()) }).append('\n')
        }
        val file = File(FilesProvider.sharedDir(this), "omr-results.csv")
        file.writeText(csv.toString())
        val uri = FilesProvider.uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OMR results")
            clipData = ClipData.newRawUri("results", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Export results"))
    }

    private fun cell(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("Delete all saved results?")
            .setPositiveButton("Delete") { _, _ -> store.clearHistory(); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
