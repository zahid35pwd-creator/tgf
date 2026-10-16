package com.omrscanner.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.ProgressRing
import com.omrscanner.core.ArgbImage
import com.omrscanner.core.Batch
import com.omrscanner.core.DocxReport
import com.omrscanner.core.ReportEntry
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Checks every page of a PDF (e.g. a stack of sheets from a scanner) and produces a Word report with one
 * row per candidate, sorted by roll / registration number. Also opens PDFs shared from other apps.
 */
class BatchActivity : BaseActivity() {
    private lateinit var store: Store
    private lateinit var settings: ExamSettings
    private lateinit var page: LinearLayout
    private var entries: List<ReportEntry> = emptyList()
    private var sourceName = "OMR results"
    @Volatile private var cancelled = false
    private var addedToHistory = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        settings = store.settings
        val shared = ImageSources.sharedUri(intent)
        if (shared != null) start(shared) else showIntro()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun showIntro() {
        page = scrollingPage("Many sheets")
        val hero = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(12), 0, dp(20)) }
        hero.add(iconBadge(Icon.PDF, Ui.PURPLE, Ui.PURPLE_BG, 88, 40), LinearLayout.LayoutParams(dp(88), dp(88)).apply { bottomMargin = dp(16) })
        hero.add(text("Check a whole stack at once", TextStyle.TITLE).apply { gravity = Gravity.CENTER }, wrap())
        hero.add(text("One answer sheet per PDF page — from a document scanner or a scanner app.", TextStyle.BODY, Ui.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), 0)
        }, wrap())
        page.add(hero, block(4))

        val how = card(6)
        how.add(listRow(Icon.SHEET, "Read as \"${settings.template.name}\"", "${settings.questionCount} questions · change on the Home tab", chevron = false), block(0))
        val keyed = store.storedKey(null).any { it != null } || store.setsWithKeys().isNotEmpty()
        how.add(if (keyed) listRow(Icon.KEY, "Graded with your answer key", "Each set uses its own key when entered", Ui.GREEN, Ui.GREEN_BG, chevron = false)
        else listRow(Icon.KEY, "No answer key yet", "Only the marked answers will be listed", Ui.ORANGE, Ui.ORANGE_BG, chevron = false), block(0))
        how.add(listRow(Icon.WORD, "Word report by roll number", "One row per candidate, with every answer", Ui.BLUE, Ui.PRIMARY_LIGHT, chevron = false), block(0))
        page.add(how, block(20))
        page.add(button("Choose PDF", ButtonStyle.PRIMARY, Icon.PDF) { pickPdf() }, block(0))
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try { startActivityForResult(intent, REQUEST_PDF) } catch (e: ActivityNotFoundException) { toast("No file picker found.") }
    }

    private fun start(uri: Uri) {
        cancelled = false
        addedToHistory = false
        val footer = footerBar(button("Cancel", ButtonStyle.OUTLINE, Icon.CROSS) { cancelled = true })
        page = scrollingPage("Checking sheets", back = false, footer = footer)
        sourceName = fileName(uri)?.substringBeforeLast('.') ?: "OMR results"
        val strip = statusStrip("CHECKING  //  OPENING PDF", DotColor.BUSY)
        page.add(strip, block(12))
        val stripText = strip.getChildAt(1) as android.widget.TextView

        val top = card(18)
        val row = horizontal()
        val ring = ProgressRing(this, Ui.CARD_ALT, Ui.PRIMARY, Ui.TEXT).apply { strokeDp = 7f; label = "…" }
        row.add(ring, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(16) })
        val t = vertical()
        t.add(text(sourceName, TextStyle.HEADING).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, wrap())
        val status = text("Opening the PDF…", TextStyle.CAPTION, Ui.MUTED)
        t.add(status, wrap())
        row.add(t, weighted())
        top.add(row, block(0))
        page.add(top, block(16))
        page.add(sectionHeader("Sheets"), block(4))
        val live = card(6)
        live.visibility = View.GONE
        page.add(live, block(0))

        val options = settings.readerOptions
        Thread {
            val out = ArrayList<ReportEntry>()
            var error: String? = null
            try {
                val fd = contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Could not open the file.")
                PdfRenderer(fd).use { pdf ->
                    val n = pdf.pageCount
                    for (i in 0 until n) {
                        if (cancelled) break
                        runOnUiThread {
                            ring.label = "${i + 1}/$n"
                            ring.setProgress(i.toFloat() / n)
                            status.text = "Reading page ${i + 1} of $n"
                            stripText.text = "CHECKING  //  PAGE ${i + 1} OF $n"
                        }
                        val image = pdf.openPage(i).use { p ->
                            // About 2000 px on the long side: plenty for the reader, modest on memory.
                            val scale = 2000.0 / max(p.width, p.height)
                            val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(0xFFFFFFFF.toInt())
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val px = IntArray(bmp.width * bmp.height)
                            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                            ArgbImage(bmp.width, bmp.height, px).also { bmp.recycle() }
                        }
                        val e = Batch.check("Page ${i + 1}", image, options, { set -> store.keyFor(set) }, settings.marking, settings.labels).first
                        out.add(e)
                        runOnUiThread {
                            if (live.childCount > 0) live.addView(divider(), 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) })
                            live.addView(entryRow(e), 0)
                            live.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                error = "Not enough memory to render the PDF pages."
            } catch (e: Exception) {
                error = "Could not read this PDF (${e.message ?: e.javaClass.simpleName})."
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                entries = out
                if (out.isEmpty()) showError(error ?: "Stopped before any page was checked.")
                else showResults(error ?: if (cancelled) "Stopped early: ${out.size} pages checked." else null)
            }
        }.start()
    }

    private fun entryRow(e: ReportEntry): View {
        val g = e.grade
        val row = horizontal().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val badge = when {
            e.error != null -> iconBadge(Icon.WARNING, Ui.RED, Ui.RED_BG, 46, 22)
            g == null -> iconBadge(Icon.SHEET, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 46, 22)
            else -> ResultsTab.scoreBadge(this, g.percent)
        }
        row.add(badge, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
        val t = vertical()
        t.add(text(if (e.error != null) "Not read" else "Roll ${e.id.ifEmpty { "?" }}", 15f, if (e.error != null) Ui.RED else Ui.TEXT, true), wrap())
        t.add(text(listOfNotNull(e.source, e.error ?: e.details.joinToString(" · ") { "${it.first} ${it.second}" }.takeIf { it.isNotBlank() }).joinToString(" · "), 12.5f, Ui.MUTED).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, wrap())
        row.add(t, weighted())
        if (g != null) row.add(text("${fmt(g.score)}/${fmt(g.maxScore)}", 15f, Ui.TEXT, true), wrap().apply { marginStart = dp(8) })
        return row
    }

    private fun showError(message: String) {
        page = scrollingPage("Many sheets")
        page.add(spacer(32), block(0))
        page.add(emptyState(Icon.WARNING, "Couldn't check the PDF", message, "Choose another PDF") { pickPdf() }, block(0))
    }

    private fun showResults(warning: String?) {
        val footer = footerBar(
            button("Share", ButtonStyle.SECONDARY, Icon.SHARE) { shareReport() },
            button("Save Word", ButtonStyle.PRIMARY, Icon.WORD) { saveReport() },
        )
        page = scrollingPage("Report ready", footer = footer)
        val read = entries.count { it.error == null }
        page.add(statusStrip("REPORT READY  //  $read / ${entries.size} READ", if (read == entries.size) DotColor.OK else DotColor.BUSY), block(12))
        val graded = entries.mapNotNull { it.grade }

        val top = card(18)
        top.add(text(sourceName.uppercase(), TextStyle.OVERLINE, Ui.PRIMARY).apply { maxLines = 1 }, block(4))
        top.add(text("$read of ${entries.size} sheets read", TextStyle.TITLE), block(12))
        val tiles = horizontal()
        tiles.add(statTile("$read", "Read", Ui.GREEN, Ui.GREEN_BG), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile("${entries.size - read}", "Not read", Ui.RED, Ui.RED_BG), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile(if (graded.isEmpty()) "—" else pct(graded.map { it.percent }.average()), "Average", Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT), weighted())
        top.add(tiles, block(0))
        page.add(top, block(12))
        warning?.let { page.add(text(it, 14f, Ui.ORANGE).apply { setPadding(dp(4), 0, dp(4), 0) }, block(12)) }

        val actions = card(6)
        actions.add(listRow(Icon.CHART, "Add to saved results", "Shows them on the Results tab", Ui.GREEN, Ui.GREEN_BG) { addToHistory() }, block(0))
        actions.add(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) })
        actions.add(listRow(Icon.PDF, "Check another PDF", null, Ui.PURPLE, Ui.PURPLE_BG) { pickPdf() }, block(0))
        page.add(actions, block(16))

        page.add(sectionHeader("Sheets by roll"), block(4))
        val list = card(6)
        sortedEntries().forEachIndexed { i, e ->
            if (i > 0) list.add(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) })
            list.add(entryRow(e), block(0))
        }
        page.add(list, block(0))
    }

    private fun sortedEntries() = entries.sortedWith(compareBy({ it.error != null }, { it.id.isEmpty() }, { it.id.padStart(12, '0') }))

    private fun reportFileName() = "${sourceName.take(40)} results.docx"

    private fun writeReport(out: OutputStream) {
        val subtitle = listOf(
            settings.template.name,
            settings.examName,
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date()),
        ).filter { it.isNotBlank() }.joinToString(" • ")
        DocxReport.write(out, settings.examName.ifBlank { sourceName }, subtitle, entries, settings.labels)
    }

    private fun saveReport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = DOCX
            putExtra(Intent.EXTRA_TITLE, reportFileName())
        }
        try { startActivityForResult(intent, REQUEST_SAVE) } catch (e: ActivityNotFoundException) { shareReport() }
    }

    private fun shareReport() {
        val file = File(FilesProvider.sharedDir(this), "omr-report.docx")
        file.outputStream().use { writeReport(it) }
        val uri = FilesProvider.uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = DOCX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, reportFileName())
            clipData = ClipData.newRawUri("report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun addToHistory() {
        if (addedToHistory) { snackbar("Already added to saved results"); return }
        var added = 0
        for (e in entries) {
            val g = e.grade ?: continue
            store.addResult(
                SavedResult(
                    time = System.currentTimeMillis(), exam = listOf(settings.examName, e.source).filter { it.isNotBlank() }.joinToString(" • "),
                    roll = e.id, score = g.score, maxScore = g.maxScore, correct = g.correct, wrong = g.wrong,
                    blank = g.blank, multiple = g.multiple, key = ScanActivity.keyString(g), examName = settings.examName, answers = e.answers.joinToString("") { a ->
                        when {
                            a == "—" -> "-"
                            a.contains(',') -> "*"
                            else -> ('A' + settings.labels.indexOf(a).coerceAtLeast(0)).toString()
                        }
                    },
                ),
            )
            added++
        }
        addedToHistory = added > 0
        snackbar(if (added == 0) "Nothing to add: set an answer key first" else "$added results added")
    }

    private fun fileName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_PDF -> start(uri)
            REQUEST_SAVE -> try {
                contentResolver.openOutputStream(uri)?.use { writeReport(it) }
                snackbar("Report saved")
            } catch (e: Exception) {
                toast("Could not save the report: ${e.message}")
            }
        }
    }

    companion object {
        private const val REQUEST_PDF = 201
        private const val REQUEST_SAVE = 202
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
