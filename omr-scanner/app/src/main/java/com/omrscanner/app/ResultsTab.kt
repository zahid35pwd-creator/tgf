package com.omrscanner.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.omrscanner.app.ui.BarChart
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable
import com.omrscanner.core.DocxReport
import com.omrscanner.core.GradeResult
import com.omrscanner.core.QuestionGrade
import com.omrscanner.core.ReportEntry
import com.omrscanner.core.Verdict
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved results: class summary, search and sort, details, delete with undo, and Word / CSV export. */
class ResultsTab(private val a: MainActivity) {
    private lateinit var list: LinearLayout

    fun build(): View = with(a) {
        val results = store.history()
        val export = iconButton(Icon.SHARE) { exportMenu() }
        val (root, column) = if (results.isEmpty()) tabPage("Results") else tabPage("Results", export)
        if (results.isEmpty()) {
            column.add(spacer(40), block(0))
            column.add(emptyState(Icon.CHART, "No saved results", "After scanning a sheet, tap Save. Results from a PDF of sheets can be added too.",
                "Scan a sheet") { scanSheet(true) }, block(0))
            return root
        }

        column.add(summary(results), block(16))

        val (searchBox, search) = inputField("", query, InputType.TYPE_CLASS_TEXT, "Search roll or exam")
        (searchBox.getChildAt(0)).visibility = View.GONE
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(IconDrawable(Icon.SEARCH, Ui.MUTED, dp(20)), null, null, null)
        search.compoundDrawablePadding = dp(10)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s.toString(); fill() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, aft: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        column.add(searchBox, block(10))

        val sorts = horizontal()
        Sort.entries.forEach { s ->
            sorts.add(choiceChip(s.label, s == sort) { sort = s; refresh() }, wrap().apply { marginEnd = dp(8) })
        }
        column.add(sorts, block(14))

        list = vertical()
        column.add(list, block(0))
        fill()
        root
    }

    private fun fill() { with(a) {
        list.removeAllViews()
        val q = query.trim().lowercase()
        val all = store.history()
        val shown = all.filter { q.isEmpty() || it.roll.lowercase().contains(q) || it.exam.lowercase().contains(q) }.let { r ->
            when (sort) {
                Sort.NEWEST -> r.sortedByDescending { it.time }
                Sort.SCORE -> r.sortedByDescending { percentOf(it) }
                Sort.ROLL -> r.sortedWith(compareBy({ it.roll.isBlank() }, { it.roll.padStart(12, '0') }))
            }
        }
        if (shown.isEmpty()) {
            list.add(text("No result matches \"$query\".", TextStyle.BODY, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) }, block(0))
            return@with
        }
        val c = card(6)
        shown.forEachIndexed { i, r ->
            if (i > 0) c.add(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) })
            c.add(resultRow(this, r) { details(r) }, block(0))
        }
        list.add(c, block(0))
    } }

    private fun summary(results: List<SavedResult>): View = with(a) {
        val c = card(18)
        val pcts = results.map { percentOf(it) }
        c.add(text("CLASS SUMMARY", TextStyle.OVERLINE, Ui.PRIMARY), block(10))
        val tiles = horizontal()
        tiles.add(statTile("${results.size}", "Sheets", Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile(pct(pcts.average()), "Average", Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile(pct(pcts.max()), "Highest", Ui.GREEN, Ui.GREEN_BG), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile(pct(pcts.min()), "Lowest", Ui.RED, Ui.RED_BG), weighted())
        c.add(tiles, block(16))
        c.add(text("Score distribution", TextStyle.LABEL, Ui.TEXT), block(6))
        val bands = listOf(
            Triple("80–100%", pcts.count { it >= 80 }, Ui.GREEN),
            Triple("60–79%", pcts.count { it >= 60 && it < 80 }, Ui.PRIMARY),
            Triple("40–59%", pcts.count { it >= 40 && it < 60 }, Ui.ORANGE),
            Triple("0–39%", pcts.count { it < 40 }, Ui.RED),
        )
        c.add(BarChart(this, bands, Ui.CARD_ALT, Ui.TEXT, Ui.MUTED), block(0))
        c
    }

    private fun details(r: SavedResult) = a.bottomSheet(null) { col, d ->
        with(a) {
            val head = horizontal()
            head.add(scoreBadge(this, percentOf(r), 56), LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(14) })
            val t = vertical()
            t.add(text("Roll ${r.roll.ifBlank { "?" }}", TextStyle.TITLE), wrap())
            t.add(text("${fmt(r.score)} / ${fmt(r.maxScore)} marks", TextStyle.BODY, Ui.MUTED), wrap())
            head.add(t, weighted())
            col.add(head, block(16))
            val tiles = horizontal()
            tiles.add(statTile("${r.correct}", "Correct", Ui.GREEN, Ui.GREEN_BG), weighted().apply { marginEnd = dp(6) })
            tiles.add(statTile("${r.wrong}", "Wrong", Ui.RED, Ui.RED_BG), weighted().apply { marginEnd = dp(6) })
            tiles.add(statTile("${r.blank}", "Blank", Ui.ORANGE, Ui.ORANGE_BG), weighted().apply { marginEnd = dp(6) })
            tiles.add(statTile("${r.multiple}", "Multiple", Ui.PURPLE, Ui.PURPLE_BG), weighted())
            col.add(tiles, block(14))
            val meta = listOf(r.exam, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.time))).filter { it.isNotBlank() }
            col.add(text(meta.joinToString(" · "), TextStyle.CAPTION, Ui.MUTED), block(10))
            if (r.answers.isNotEmpty()) {
                col.add(text("Marked answers", TextStyle.LABEL), block(4))
                col.add(text(r.answers.chunked(5).joinToString("  "), 14f, Ui.MUTED).apply { typeface = android.graphics.Typeface.MONOSPACE }, block(16))
            }
            col.add(button("Delete result", ButtonStyle.DANGER, Icon.TRASH) {
                d.dismiss()
                val before = store.history()
                store.setHistory(before.filter { it !== r && it.time != r.time })
                refresh()
                snackbar("Result deleted", "Undo") { store.setHistory(before); refresh() }
            }, block(0))
        }
    }

    private fun exportMenu() = a.bottomSheet("Export results") { col, d ->
        with(a) {
            col.add(listRow(Icon.WORD, "Word report (.docx)", "Table of all candidates, sorted by roll", Ui.BLUE, Ui.PRIMARY_LIGHT) { d.dismiss(); shareWord() }, block(0))
            col.add(listRow(Icon.CSV, "Spreadsheet (.csv)", "Opens in Excel or Google Sheets", Ui.GREEN, Ui.GREEN_BG) { d.dismiss(); shareCsv() }, block(0))
            col.add(listRow(Icon.TRASH, "Delete all results", null, Ui.RED, Ui.RED_BG) {
                d.dismiss()
                confirm("Delete all results?", "All ${store.history().size} saved results will be removed.", "Delete", danger = true) {
                    val before = store.history()
                    store.clearHistory()
                    refresh()
                    snackbar("All results deleted", "Undo") { store.setHistory(before); refresh() }
                }
            }, block(0))
        }
    }

    private fun share(file: File, type: String, title: String) {
        val uri = FilesProvider.uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        a.startActivity(Intent.createChooser(send, "Share $title"))
    }

    private fun shareWord() {
        val s = a.store.settings
        val labels = s.labels
        fun label(ch: Char) = when (ch) {
            '-' -> "—"
            '*' -> "*"
            else -> labels.getOrNull(ch - 'A') ?: ch.toString()
        }
        val dates = SimpleDateFormat("d MMM, HH:mm", Locale.US)
        val entries = a.store.history().map { r ->
            ReportEntry(
                source = dates.format(Date(r.time)),
                id = r.roll,
                details = if (r.exam.isNotBlank()) listOf("Exam" to r.exam) else emptyList(),
                grade = gradeOf(r),
                answers = r.answers.map { label(it) },
            )
        }
        val file = File(FilesProvider.sharedDir(a), "omr-results.docx")
        file.outputStream().use { DocxReport.write(it, s.examName.ifBlank { "OMR results" }, "Saved results • ${SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())}", entries, labels) }
        share(file, BatchActivity.DOCX, "OMR results.docx")
    }

    private fun shareCsv() {
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val csv = StringBuilder("Time,Exam,Roll,Score,Max,Percent,Correct,Wrong,Blank,Multiple,Answers\n")
        for (r in a.store.history()) {
            csv.append(listOf(
                iso.format(Date(r.time)), r.exam, r.roll, fmt(r.score), fmt(r.maxScore), String.format(Locale.US, "%.1f", percentOf(r)),
                r.correct, r.wrong, r.blank, r.multiple, r.answers,
            ).joinToString(",") { cell(it.toString()) }).append('\n')
        }
        val file = File(FilesProvider.sharedDir(a), "omr-results.csv")
        file.writeText(csv.toString())
        share(file, "text/csv", "OMR results.csv")
    }

    private fun cell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    enum class Sort(val label: String) { NEWEST("Newest"), SCORE("Score"), ROLL("Roll") }

    companion object {
        private var query = ""
        private var sort = Sort.NEWEST

        fun percentOf(r: SavedResult) = if (r.maxScore > 0) 100 * r.score / r.maxScore else 0.0

        /** Rebuilds per-question verdicts from the saved answers and key (null for old results without the key). */
        fun gradeOf(r: SavedResult): GradeResult? {
            if (r.key.isEmpty()) return null
            val qs = r.answers.mapIndexed { q, ch ->
                val k = r.key.getOrNull(q)?.takeIf { it in 'A'..'Z' }?.let { it - 'A' }
                val marked = if (ch in 'A'..'Z') listOf(ch - 'A') else emptyList()
                val verdict = when {
                    k == null -> Verdict.UNKEYED
                    ch == '-' -> Verdict.BLANK
                    ch == '*' -> Verdict.MULTIPLE
                    marked.firstOrNull() == k -> Verdict.CORRECT
                    else -> Verdict.WRONG
                }
                QuestionGrade(q, marked, k, verdict, false)
            }
            return GradeResult(qs, r.score, r.maxScore)
        }

        /** A circular badge with the percentage, coloured by band. */
        fun scoreBadge(c: Context, percent: Double, sizeDp: Int = 46) = c.text(pct(percent), if (sizeDp >= 56) 15f else 13f, Ui.bandColor(percent), true).apply {
            gravity = Gravity.CENTER
            val bg = when (Ui.bandColor(percent)) { Ui.GREEN -> Ui.GREEN_BG; Ui.ORANGE -> Ui.ORANGE_BG; Ui.RED -> Ui.RED_BG; else -> Ui.PRIMARY_LIGHT }
            background = rounded(bg, c.dp(sizeDp / 2).toFloat())
            layoutParams = LinearLayout.LayoutParams(c.dp(sizeDp), c.dp(sizeDp))
        }

        /** A compact row for one result: score badge, roll, exam/date and the score. */
        fun resultRow(c: Context, r: SavedResult, onClick: () -> Unit): View = with(c) {
            val row = horizontal().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
            row.add(scoreBadge(this, percentOf(r)), LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
            val t = vertical()
            t.add(text("Roll ${r.roll.ifBlank { "?" }}", 15f, Ui.TEXT, true), wrap())
            val when_ = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(r.time))
            t.add(text(listOf(r.exam, when_).filter { it.isNotBlank() }.joinToString(" · "), 12.5f, Ui.MUTED).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, wrap())
            row.add(t, weighted())
            row.add(text("${fmt(r.score)}/${fmt(r.maxScore)}", 15f, Ui.TEXT, true), wrap().apply { marginStart = dp(8) })
            row.background = ripple(rounded(android.graphics.Color.TRANSPARENT, dp(16).toFloat()), dp(16).toFloat())
            row.setOnClickListener { onClick() }
            row
        }
    }
}
