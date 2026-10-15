package com.omrscanner.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
class BatchActivity : Activity() {
    private lateinit var store: Store
    private lateinit var settings: ExamSettings
    private lateinit var page: LinearLayout
    private var entries: List<ReportEntry> = emptyList()
    private var sourceName = "OMR results"
    @Volatile private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        settings = store.settings
        title = "Check a PDF of sheets"
        val shared = ImageSources.sharedUri(intent)
        if (shared != null) start(shared) else showIntro()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun showIntro() {
        page = scrollingPage()
        val c = card()
        c.add(text("Check many sheets at once", 20f, bold = true), block(8))
        c.add(text(
            "Choose a PDF with one answer sheet per page, for example from a document scanner or a scanner app. " +
                "Every page is checked as a \"${settings.template.name}\" with your current answer key, " +
                "and you get a Word file listing each candidate by roll / registration number.",
            15f, Ui.MUTED,
        ), block(12))
        val keyed = store.storedKey(null).any { it != null } || store.setsWithKeys().isNotEmpty()
        if (!keyed) c.add(text("No answer key is set yet, so only the marked answers will be listed.", 14f, Ui.ORANGE), block(12))
        c.add(button("Choose PDF") { pickPdf() }, block(0))
        page.add(c)
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try { startActivityForResult(intent, REQUEST_PDF) } catch (e: ActivityNotFoundException) { toast("No file picker found.") }
    }

    private fun start(uri: Uri) {
        page = scrollingPage()
        val c = card()
        c.add(text("Checking sheets…", 20f, bold = true), block(12))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        c.add(progress, block(8))
        val status = c.add(text("Opening the PDF…", 15f, Ui.MUTED), block(0)) as TextView
        page.add(c)
        sourceName = fileName(uri)?.substringBeforeLast('.') ?: "OMR results"

        val options = settings.readerOptions
        Thread {
            val out = ArrayList<ReportEntry>()
            var error: String? = null
            try {
                val fd = contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Could not open the file.")
                PdfRenderer(fd).use { pdf ->
                    val n = pdf.pageCount
                    for (i in 0 until n) {
                        if (cancelled) return@Thread
                        runOnUiThread {
                            progress.isIndeterminate = false
                            progress.max = n
                            progress.progress = i
                            status.text = "Page ${i + 1} of $n"
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
                        out.add(Batch.check("Page ${i + 1}", image, options, { set -> store.keyFor(set) }, settings.marking, settings.labels).first)
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
                if (error != null && out.isEmpty()) showError(error) else showResults(error)
            }
        }.start()
    }

    private fun showError(message: String) {
        page = scrollingPage()
        val c = card()
        c.add(text("Couldn't check the PDF", 20f, Ui.RED, bold = true), block(8))
        c.add(text(message, 15f), block(12))
        c.add(button("Choose another PDF") { pickPdf() }, block(0))
        page.add(c)
    }

    private fun showResults(warning: String?) {
        page = scrollingPage()
        val read = entries.count { it.error == null }
        val top = card()
        top.add(text("$read of ${entries.size} sheets read", 20f, bold = true), block(6))
        warning?.let { top.add(text(it, 14f, Ui.ORANGE), block(6)) }
        if (read < entries.size) top.add(text("Pages that could not be read are listed at the end of the report.", 14f, Ui.MUTED), block(10))
        top.add(button("Save Word report") { saveReport() }, block(8))
        top.add(button("Share Word report", ButtonStyle.SECONDARY) { shareReport() }, block(8))
        top.add(button("Add to saved results", ButtonStyle.SECONDARY) { addToHistory() }, block(0))
        page.add(top, block(12))

        val list = card().apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        for (e in sortedEntries()) {
            val row = horizontal().apply { setPadding(dp(4), dp(6), dp(4), dp(6)) }
            row.add(text(e.id.ifEmpty { "?" }, 15f, bold = true), weighted(1.4f))
            val g = e.grade
            val (label, color) = when {
                e.error != null -> "Not read" to Ui.RED
                g == null -> "No key" to Ui.MUTED
                else -> "${fmt(g.score)}/${fmt(g.maxScore)}" to Ui.PRIMARY
            }
            row.add(text(label, 15f, color, bold = true), weighted(1f))
            row.add(text(e.source, 13f, Ui.MUTED), weighted(0.8f))
            list.add(row, block(0))
            e.error?.let { list.add(text(it, 12f, Ui.MUTED), block(4)) }
        }
        page.add(list, block(12))
        page.add(button("Check another PDF", ButtonStyle.SECONDARY) { pickPdf() })
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
        var added = 0
        for (e in entries) {
            val g = e.grade ?: continue
            store.addResult(
                SavedResult(
                    time = System.currentTimeMillis(), exam = listOf(settings.examName, e.source).filter { it.isNotBlank() }.joinToString(" • "),
                    roll = e.id, score = g.score, maxScore = g.maxScore, correct = g.correct, wrong = g.wrong,
                    blank = g.blank, multiple = g.multiple, answers = e.answers.joinToString("") { a ->
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
        toast(if (added == 0) "Nothing to add: set an answer key first" else "$added results added")
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
                toast("Report saved")
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
