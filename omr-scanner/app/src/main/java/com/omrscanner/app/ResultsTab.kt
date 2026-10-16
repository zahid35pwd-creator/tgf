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

/** Saved results, exam by exam: tap an exam for its summary, students and Word / spreadsheet / PDF export. */
class ResultsTab(private val a: MainActivity) {
    private lateinit var list: LinearLayout

    private fun exams(): Map<String, List<SavedResult>> =
        a.store.history().groupBy { it.examGroup }.toList().sortedByDescending { (_, r) -> r.maxOf { it.time } }.toMap()

    fun build(): View = with(a) {
        val exams = exams()
        val open = openExam?.takeIf { it in exams }
        openExam = open
        return if (open == null) examList(exams) else examPage(open, exams.getValue(open))
    }

    // ---- All exams ---------------------------------------------------------------------------

    private fun examList(exams: Map<String, List<SavedResult>>): View = with(a) {
        val (root, column) = tabPage("Results", iconButton(Icon.MORE) { appMenu() })
        if (exams.isEmpty()) {
            column.add(spacer(40), block(0))
            column.add(emptyState(Icon.CHART, "No saved results", "After scanning a sheet, tap Save. Results from a PDF of sheets can be added too.",
                "Go to Scanner") { show(MainActivity.Tab.SCANNER) }, block(0))
            return root
        }
        column.add(text("${exams.size} exam${if (exams.size > 1) "s" else ""} · tap one for details and export", TextStyle.CAPTION, Ui.MUTED).apply {
            setPadding(dp(4), 0, 0, dp(10))
        }, wrap())
        for ((name, results) in exams) column.add(examCard(name, results), block(10))
        root
    }

    private fun examCard(name: String, results: List<SavedResult>): View = with(a) {
        val c = clickableCard(16) { openExam = name; refresh() }
        val avg = results.map { percentOf(it) }.average()
        val row = horizontal()
        row.add(scoreBadge(this, avg, 52), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text(name, 16f, Ui.TEXT, true).apply { maxLines = 2 }, wrap())
        val last = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(results.maxOf { it.time }))
        t.add(text("${results.size} sheet${if (results.size > 1) "s" else ""} · average ${pct(avg)} · $last", 12.5f, Ui.MUTED), wrap())
        row.add(t, weighted())
        row.add(android.widget.ImageView(this).apply { setImageDrawable(IconDrawable(Icon.CHEVRON, Ui.MUTED, dp(18))) }, wrap())
        c.add(row, block(0))
        c
    }

    // ---- One exam ----------------------------------------------------------------------------

    private fun examPage(name: String, results: List<SavedResult>): View = with(a) {
        val back = iconButton(Icon.BACK) { openExam = null; refresh() }
        val (root, column) = tabPage(name, iconButton(Icon.MORE) { appMenu() })
        // Put the back arrow in front of the title.
        ((root as LinearLayout).getChildAt(0) as LinearLayout).addView(back, 0, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(4) })

        column.add(summary(results), block(14))

        column.add(sectionHeader("Export"), block(4))
        val ex = horizontal()
        ex.add(button("Word", ButtonStyle.SECONDARY, Icon.WORD) { shareWord(name, results) }, weighted().apply { marginEnd = dp(8) })
        ex.add(button("Excel", ButtonStyle.SECONDARY, Icon.CSV) { shareCsv(name, results) }, weighted().apply { marginEnd = dp(8) })
        ex.add(button("PDF", ButtonStyle.SECONDARY, Icon.PDF) { sharePdf(name, results) }, weighted())
        column.add(ex, block(18))

        val (searchBox, search) = inputField("", query, InputType.TYPE_CLASS_TEXT, "Search roll number")
        (searchBox.getChildAt(0)).visibility = View.GONE
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(IconDrawable(Icon.SEARCH, Ui.MUTED, dp(20)), null, null, null)
        search.compoundDrawablePadding = dp(10)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s.toString(); fill(results) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, aft: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        column.add(searchBox, block(10))

        val sorts = horizontal()
        Sort.entries.forEach { s -> sorts.add(choiceChip(s.label, s == sort) { sort = s; refresh() }, wrap().apply { marginEnd = dp(8) }) }
        column.add(sorts, block(14))

        list = vertical()
        column.add(list, block(16))
        fill(results)

        column.add(button("Delete this exam's results", ButtonStyle.DANGER, Icon.TRASH) {
            confirm("Delete \"$name\"?", "All ${results.size} saved results of this exam will be removed.", "Delete", danger = true) {
                val before = store.history()
                store.setHistory(before.filter { it.examGroup != name })
                openExam = null
                refresh()
                snackbar("Exam deleted", "Undo") { store.setHistory(before); refresh() }
            }
        }, block(0))
        root
    }

    private fun fill(results: List<SavedResult>) { with(a) {
        list.removeAllViews()
        val q = query.trim().lowercase()
        val shown = results.filter { q.isEmpty() || it.roll.lowercase().contains(q) }.let { r ->
            when (sort) {
                Sort.ROLL -> r.sortedWith(compareBy({ it.roll.isBlank() }, { it.roll.padStart(12, '0') }))
                Sort.SCORE -> r.sortedByDescending { percentOf(it) }
                Sort.NEWEST -> r.sortedByDescending { it.time }
            }
        }
        if (shown.isEmpty()) {
            list.add(text("No roll number matches \"$query\".", TextStyle.BODY, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) }, block(0))
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
        c.add(text("SUMMARY", TextStyle.OVERLINE, Ui.PRIMARY), block(10))
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
            tiles.add(statTile("${r.blank}", "Blank", Ui.ORANGE, Ui.ORANGE_BG), weighted())
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

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9ঀ-৿ _-]"), "").trim().take(40).ifBlank { "OMR results" }

    private fun byRoll(results: List<SavedResult>) = results.sortedWith(compareBy({ it.roll.isBlank() }, { it.roll.padStart(12, '0') }))

    private fun shareWord(name: String, results: List<SavedResult>) {
        val labels = a.store.settings.labels
        fun label(ch: Char) = when (ch) {
            '-' -> "—"
            '*' -> "*"
            else -> labels.getOrNull(ch - 'A') ?: ch.toString()
        }
        val dates = SimpleDateFormat("d MMM, HH:mm", Locale.US)
        val entries = results.map { r ->
            ReportEntry(source = dates.format(Date(r.time)), id = r.roll, details = emptyList(), grade = gradeOf(r), answers = r.answers.map { label(it) })
        }
        val file = File(FilesProvider.sharedDir(a), "${safe(name)} results.docx")
        file.outputStream().use { DocxReport.write(it, name, "${results.size} sheets • ${SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())}", entries, labels) }
        share(file, BatchActivity.DOCX, file.name)
    }

    private fun shareCsv(name: String, results: List<SavedResult>) {
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val csv = StringBuilder("\uFEFFRoll,Score,Max,Percent,Correct,Wrong,Blank,Multiple,Time,Answers\n")
        for (r in byRoll(results)) {
            csv.append(listOf(
                r.roll, fmt(r.score), fmt(r.maxScore), String.format(Locale.US, "%.1f", percentOf(r)),
                r.correct, r.wrong, r.blank, r.multiple, iso.format(Date(r.time)), r.answers,
            ).joinToString(",") { cell(it.toString()) }).append('\n')
        }
        val file = File(FilesProvider.sharedDir(a), "${safe(name)} results.csv")
        file.writeText(csv.toString())
        share(file, "text/csv", file.name)
    }

    /** A simple A4 table: one row per student, sorted by roll, with a summary line. */
    private fun sharePdf(name: String, results: List<SavedResult>) {
        val doc = android.graphics.pdf.PdfDocument()
        val title = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
        val body = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f }
        val bold = android.graphics.Paint(body).apply { isFakeBoldText = true }
        val line = android.graphics.Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 0.6f }
        val cols = listOf("#" to 40f, "Roll" to 70f, "Score" to 170f, "%" to 240f, "Right" to 290f, "Wrong" to 340f, "Blank" to 395f, "Multi" to 445f, "Date" to 495f)
        val rows = byRoll(results)
        val pcts = results.map { percentOf(it) }
        val dates = SimpleDateFormat("d MMM", Locale.US)
        var pageNo = 0
        var i = 0
        try {
            while (i < rows.size || pageNo == 0) {
                pageNo++
                val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                val c = page.canvas
                var y = 50f
                if (pageNo == 1) {
                    c.drawText(name, 40f, y, title); y += 20f
                    c.drawText("${results.size} sheets · average ${pct(pcts.average())} · highest ${pct(pcts.max())} · lowest ${pct(pcts.min())}", 40f, y, body); y += 26f
                }
                for ((h, x) in cols) c.drawText(h, x, y, bold)
                y += 6f; c.drawLine(40f, y, 555f, y, line); y += 14f
                while (i < rows.size && y < 800f) {
                    val r = rows[i]
                    val cells = listOf("${i + 1}", r.roll.ifBlank { "?" }, "${fmt(r.score)}/${fmt(r.maxScore)}", pct(percentOf(r)),
                        "${r.correct}", "${r.wrong}", "${r.blank}", "${r.multiple}", dates.format(Date(r.time)))
                    cells.forEachIndexed { k, v -> c.drawText(v, cols[k].second, y, body) }
                    y += 16f
                    i++
                }
                c.drawText("Page $pageNo", 520f, 825f, body)
                doc.finishPage(page)
            }
            val file = File(FilesProvider.sharedDir(a), "${safe(name)} results.pdf")
            file.outputStream().use { doc.writeTo(it) }
            share(file, "application/pdf", file.name)
        } catch (e: Exception) {
            a.toast("Could not make the PDF: ${e.message}")
        } finally {
            doc.close()
        }
    }

    private fun cell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    enum class Sort(val label: String) { ROLL("Roll"), SCORE("Score"), NEWEST("Newest") }

    companion object {
        private var query = ""
        private var sort = Sort.ROLL

        /** The exam whose page is open, or null for the list of exams. */
        var openExam: String? = null

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
