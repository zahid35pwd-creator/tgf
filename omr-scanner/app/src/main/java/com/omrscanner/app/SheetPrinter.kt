package com.omrscanner.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.omrscanner.core.SheetCanvas
import com.omrscanner.core.SheetLayout
import com.omrscanner.core.SheetPainter
import com.omrscanner.core.SheetSpec
import com.omrscanner.core.TextAlign
import java.io.File
import kotlin.math.min

/** Renders a blank OMR sheet into an A4 PDF so the user can download or share it. */
object SheetPrinter {
    /** Draws the blank sheet into an A4 PDF and opens the share sheet (Save to Files, Drive, WhatsApp…). */
    fun sharePdf(activity: Activity, spec: SheetSpec) {
        val doc = PdfDocument()
        val file = File(FilesProvider.sharedDir(activity), "omr-sheet-${spec.questionCount}q-${spec.optionCount}opt.pdf")
        try {
            // A4 in PostScript points.
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val scale = min(595.0 / SheetLayout.PAGE_WIDTH, 842.0 / SheetLayout.PAGE_HEIGHT)
            SheetPainter.paint(
                AndroidSheetCanvas(page.canvas, scale,
                    (595 - SheetLayout.PAGE_WIDTH * scale) / 2,
                    (842 - SheetLayout.PAGE_HEIGHT * scale) / 2),
                spec,
            )
            doc.finishPage(page)
            file.outputStream().use { doc.writeTo(it) }
        } catch (e: Exception) {
            activity.toast("Could not make the PDF: ${e.message}")
            return
        } finally {
            doc.close()
        }
        val uri = FilesProvider.uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Save or share the blank sheet"))
    }
}

/** [SheetCanvas] on an android.graphics.Canvas, mapping millimetres to canvas units. */
class AndroidSheetCanvas(private val canvas: Canvas, private val scale: Double, private val ox: Double, private val oy: Double) : SheetCanvas {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun x(v: Double) = (ox + v * scale).toFloat()
    private fun y(v: Double) = (oy + v * scale).toFloat()
    private fun len(v: Double) = (v * scale).toFloat()

    private fun fill(color: Int) = paint.apply { style = Paint.Style.FILL; this.color = color }
    private fun stroke(width: Double, color: Int) = paint.apply { style = Paint.Style.STROKE; strokeWidth = len(width); this.color = color }

    override fun fillRect(left: Double, top: Double, width: Double, height: Double, color: Int) =
        canvas.drawRect(x(left), y(top), x(left + width), y(top + height), fill(color))

    override fun strokeRect(left: Double, top: Double, width: Double, height: Double, stroke: Double, color: Int) =
        canvas.drawRect(x(left), y(top), x(left + width), y(top + height), stroke(stroke, color))

    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) = canvas.drawCircle(x(cx), y(cy), len(r), fill(color))

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Double, color: Int) =
        canvas.drawCircle(x(cx), y(cy), len(r), stroke(stroke, color))

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Double, color: Int) =
        canvas.drawLine(x(x1), y(y1), x(x2), y(y2), stroke(stroke, color))

    override fun text(text: String, x: Double, baseline: Double, size: Double, align: TextAlign, bold: Boolean, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = len(size)
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = when (align) {
            TextAlign.LEFT -> Paint.Align.LEFT
            TextAlign.CENTER -> Paint.Align.CENTER
            TextAlign.RIGHT -> Paint.Align.RIGHT
        }
        canvas.drawText(text, x(x), y(baseline), paint)
    }
}
