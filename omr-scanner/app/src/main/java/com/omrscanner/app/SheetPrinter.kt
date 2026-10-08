package com.omrscanner.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.omrscanner.core.SheetCanvas
import com.omrscanner.core.SheetLayout
import com.omrscanner.core.SheetPainter
import com.omrscanner.core.SheetSpec
import com.omrscanner.core.TextAlign
import java.io.FileOutputStream
import kotlin.math.min

/** Prints (or saves as PDF) a blank sheet through Android's print dialog. */
object SheetPrinter {
    fun print(activity: Activity, spec: SheetSpec) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print("OMR sheet (${spec.questionCount} questions)", Adapter(activity.applicationContext, spec), attrs)
    }

    private class Adapter(private val context: Context, private val spec: SheetSpec) : PrintDocumentAdapter() {
        private var attributes: PrintAttributes? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancel: CancellationSignal?,
            callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            attributes = newAttributes
            val info = PrintDocumentInfo.Builder("omr-sheet-${spec.questionCount}q.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
            val doc = PrintedPdfDocument(context, attributes ?: return callback.onWriteFailed("No page size"))
            try {
                val page = doc.startPage(0)
                val w = page.info.pageWidth.toDouble()
                val h = page.info.pageHeight.toDouble()
                // Fit A4 into whatever paper was chosen; the reader copes with any uniform scale.
                val scale = min(w / SheetLayout.PAGE_WIDTH, h / SheetLayout.PAGE_HEIGHT)
                val canvas = AndroidSheetCanvas(page.canvas, scale, (w - SheetLayout.PAGE_WIDTH * scale) / 2, (h - SheetLayout.PAGE_HEIGHT * scale) / 2)
                SheetPainter.paint(canvas, spec)
                doc.finishPage(page)
                if (cancel?.isCanceled == true) { callback.onWriteCancelled(); return }
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
            }
        }
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
        canvas.drawRect(RectF(x(left), y(top), x(left + width), y(top + height)), stroke(stroke, color))

    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) = canvas.drawCircle(x(cx), y(cy), len(r), fill(color))

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Double, color: Int) =
        canvas.drawCircle(x(cx), y(cy), len(r), stroke(stroke, color))

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Double, color: Int) =
        canvas.drawLine(x(x1), y(y1), x(x2), y(y2), stroke(stroke, color))

    override fun text(text: String, x: Double, baseline: Double, size: Double, align: TextAlign, bold: Boolean, color: Int) {
        fill(color)
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
