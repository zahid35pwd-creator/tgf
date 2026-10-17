package com.omrscanner.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.omrscanner.core.SheetTemplate

/** A tiny preview of a sheet layout drawn straight from the template's bubble positions. */
object TemplateThumbnail {
    private val cache = HashMap<String, Bitmap>()

    /** A [widthPx] × ([widthPx] × pageRatio) bitmap of this template's bubble layout, cached per id and size. */
    fun get(template: SheetTemplate, widthPx: Int): Bitmap {
        val key = "${template.id}@${widthPx}"
        cache[key]?.let { return it }
        val heightPx = (widthPx * template.pageHeight / template.pageWidth).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFAFAFA.toInt())
        val sx = widthPx / template.pageWidth
        val sy = heightPx / template.pageHeight
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Corner markers.
        paint.color = 0xFF1F1F28.toInt()
        paint.style = Paint.Style.FILL
        val m = template.markerSize * sx
        for (mc in template.markerCenters) {
            val cx = (mc.x * sx).toFloat()
            val cy = (mc.y * sy).toFloat()
            c.drawRect(cx - m.toFloat() / 2, cy - m.toFloat() / 2, cx + m.toFloat() / 2, cy + m.toFloat() / 2, paint)
        }
        // Field bubbles (digit / letters / name).
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        paint.color = 0xFF9CA3AF.toInt()
        for (f in template.fields) for (col in f.columns) for (p in col) {
            c.drawCircle((p.x * sx).toFloat(), (p.y * sy).toFloat(), (f.radius * sx).toFloat(), paint)
        }
        // Answer bubbles — small dots so the question grid reads at a glance.
        paint.style = Paint.Style.FILL
        paint.color = 0xFF4DD5BE.toInt()
        val r = (template.answerRadius * sx).toFloat().coerceAtLeast(0.6f)
        for (q in template.questions) for (p in q) {
            c.drawCircle((p.x * sx).toFloat(), (p.y * sy).toFloat(), r, paint)
        }
        // Thin page frame.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0xFFD1D5DB.toInt()
        c.drawRect(RectF(0.5f, 0.5f, widthPx - 0.5f, heightPx - 0.5f), paint)
        cache[key] = bmp
        return bmp
    }

    /** Removes a cached entry so the next [get] redraws (e.g. after the template was edited). */
    fun invalidate(id: String) { cache.keys.removeAll { it.startsWith("$id@") } }

    @Suppress("unused")
    fun clear() = cache.clear()
}
