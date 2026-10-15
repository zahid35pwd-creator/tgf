package com.omrscanner.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Line icons drawn on a 24 × 24 grid (outline style, 2-unit strokes). */
enum class Icon {
    BACK, CHEVRON, CAMERA, GALLERY, PDF, KEY, SETTINGS, HISTORY, PRINT, ROTATE, FLIP, SHARE, CHECK, CROSS,
    HOME, SCAN, PLUS, TRASH, SEARCH, WORD, CSV, EDIT, INFO, SHEET, CHART, SAVE, WARNING, SPARK, TROPHY, NEXT, MORE,
}

class IconDrawable(private val icon: Icon, color: Int, private val sizePx: Int) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2)
        canvas.scale(s, s)
        paint(canvas)
        canvas.restore()
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    private fun Canvas.line(vararg p: Float) {
        val path = Path()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i + 1 < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
        drawPath(path, stroke)
    }

    private fun Canvas.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) = drawRoundRect(RectF(l, t, r, b), rad, rad, stroke)

    private fun Canvas.circle(x: Float, y: Float, r: Float) = drawCircle(x, y, r, stroke)

    private fun Canvas.dot(x: Float, y: Float, r: Float) = drawCircle(x, y, r, fill)

    private fun Canvas.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, stroke)

    /** A page with a folded corner. */
    private fun Canvas.page() {
        line(14f, 3f, 6f, 3f, 5f, 4f, 5f, 20f, 6f, 21f, 18f, 21f, 19f, 20f, 19f, 8f, 14f, 3f)
        line(14f, 3f, 14f, 8f, 19f, 8f)
    }

    private fun paint(c: Canvas) = with(c) {
        when (icon) {
            Icon.BACK -> { line(19f, 12f, 5f, 12f); line(11f, 6f, 5f, 12f, 11f, 18f) }
            Icon.CHEVRON -> line(9f, 6f, 15f, 12f, 9f, 18f)
            Icon.NEXT -> { line(5f, 12f, 19f, 12f); line(13f, 6f, 19f, 12f, 13f, 18f) }
            Icon.CAMERA -> {
                line(3f, 9f, 3f, 18f, 4f, 19f, 20f, 19f, 21f, 18f, 21f, 9f, 20f, 7f, 16.5f, 7f, 15f, 4.5f, 9f, 4.5f, 7.5f, 7f, 4f, 7f, 3f, 9f)
                circle(12f, 13f, 3.6f)
            }
            Icon.GALLERY -> {
                rrect(3f, 4f, 21f, 20f, 2.5f)
                circle(8.5f, 9.5f, 1.8f)
                line(3.5f, 17.5f, 9f, 12.5f, 13f, 16f, 16f, 13f, 20.5f, 17f)
            }
            Icon.PDF, Icon.SHEET -> {
                page()
                if (icon == Icon.PDF) { line(8f, 13f, 16f, 13f); line(8f, 17f, 13f, 17f) }
                else { circle(9f, 13f, 1.2f); circle(13f, 13f, 1.2f); dot(9f, 17f, 1.4f); circle(13f, 17f, 1.2f) }
            }
            Icon.WORD -> {
                page()
                line(7.5f, 12f, 9f, 18f, 12f, 13f, 15f, 18f, 16.5f, 12f)
            }
            Icon.CSV -> {
                rrect(3.5f, 4f, 20.5f, 20f, 2f)
                line(3.5f, 9.5f, 20.5f, 9.5f); line(3.5f, 14.75f, 20.5f, 14.75f); line(9.5f, 4f, 9.5f, 20f)
            }
            Icon.KEY -> {
                circle(8f, 15f, 4f)
                line(10.8f, 12.2f, 20f, 3f); line(17f, 6f, 19.5f, 8.5f); line(14.5f, 8.5f, 16.5f, 10.5f)
            }
            Icon.SETTINGS -> {
                circle(12f, 12f, 3f)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = 12f + 6.2f * Math.cos(a).toFloat(); val y1 = 12f + 6.2f * Math.sin(a).toFloat()
                    val x2 = 12f + 9f * Math.cos(a).toFloat(); val y2 = 12f + 9f * Math.sin(a).toFloat()
                    line(x1, y1, x2, y2)
                }
                circle(12f, 12f, 6.2f)
            }
            Icon.HISTORY -> {
                arc(12f, 12f, 8.5f, -200f, 290f)
                line(3.2f, 6.5f, 3.6f, 9.8f, 6.8f, 9f)
                line(12f, 7.5f, 12f, 12f, 15f, 14f)
            }
            Icon.PRINT -> {
                line(7f, 8f, 7f, 3.5f, 17f, 3.5f, 17f, 8f)
                rrect(3f, 8f, 21f, 17f, 2f)
                rrect(7f, 13.5f, 17f, 20.5f, 1f)
                dot(17.5f, 11f, 1f)
            }
            Icon.ROTATE -> {
                arc(12f, 12.5f, 7.5f, -60f, 300f)
                line(15.5f, 2.5f, 15.9f, 6f, 12.4f, 6.6f)
            }
            Icon.FLIP -> {
                line(12f, 3f, 12f, 21f)
                line(5f, 8f, 9f, 5f); line(5f, 8f, 9f, 11f); line(5f, 8f, 9.5f, 8f)
                line(19f, 16f, 15f, 13f); line(19f, 16f, 15f, 19f); line(19f, 16f, 14.5f, 16f)
            }
            Icon.SHARE -> {
                circle(18f, 5.5f, 2.5f); circle(6f, 12f, 2.5f); circle(18f, 18.5f, 2.5f)
                line(8.2f, 10.8f, 15.8f, 6.7f); line(8.2f, 13.2f, 15.8f, 17.3f)
            }
            Icon.CHECK -> line(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f)
            Icon.CROSS -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Icon.HOME -> {
                line(3.5f, 11f, 12f, 3.5f, 20.5f, 11f)
                line(5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f)
                line(10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f)
            }
            Icon.SCAN -> {
                line(3.5f, 8f, 3.5f, 3.5f, 8f, 3.5f); line(16f, 3.5f, 20.5f, 3.5f, 20.5f, 8f)
                line(20.5f, 16f, 20.5f, 20.5f, 16f, 20.5f); line(8f, 20.5f, 3.5f, 20.5f, 3.5f, 16f)
                line(6.5f, 12f, 17.5f, 12f)
            }
            Icon.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Icon.TRASH -> {
                line(4f, 6.5f, 20f, 6.5f)
                line(9f, 6.5f, 9f, 4f, 15f, 4f, 15f, 6.5f)
                line(6f, 6.5f, 7f, 20f, 17f, 20f, 18f, 6.5f)
                line(10f, 10.5f, 10f, 16f); line(14f, 10.5f, 14f, 16f)
            }
            Icon.SEARCH -> { circle(10.5f, 10.5f, 6.5f); line(15.5f, 15.5f, 20.5f, 20.5f) }
            Icon.EDIT -> {
                line(4f, 20f, 4.5f, 16f, 15.5f, 5f, 19f, 8.5f, 8f, 19.5f, 4f, 20f)
                line(13f, 7.5f, 16.5f, 11f)
            }
            Icon.INFO -> { circle(12f, 12f, 9f); line(12f, 11f, 12f, 16.5f); dot(12f, 7.8f, 1.2f) }
            Icon.WARNING -> {
                line(12f, 3.5f, 21.5f, 20f, 2.5f, 20f, 12f, 3.5f)
                line(12f, 9.5f, 12f, 14f); dot(12f, 17f, 1.1f)
            }
            Icon.CHART -> {
                line(4f, 20f, 20f, 20f)
                rrect(5.5f, 12f, 8.5f, 17.5f, 1f); rrect(10.5f, 6f, 13.5f, 17.5f, 1f); rrect(15.5f, 9.5f, 18.5f, 17.5f, 1f)
            }
            Icon.SAVE -> {
                line(12f, 4f, 12f, 15f); line(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f)
                line(4.5f, 15.5f, 4.5f, 19.5f, 19.5f, 19.5f, 19.5f, 15.5f)
            }
            Icon.SPARK -> {
                drawPath(path {
                    moveTo(12f, 2.5f); lineTo(14f, 10f); lineTo(21.5f, 12f); lineTo(14f, 14f)
                    lineTo(12f, 21.5f); lineTo(10f, 14f); lineTo(2.5f, 12f); lineTo(10f, 10f); close()
                }, stroke)
            }
            Icon.TROPHY -> {
                line(7f, 4f, 17f, 4f, 17f, 10f)
                arc(12f, 10f, 5f, 0f, 180f)
                line(7f, 10f, 7f, 4f)
                line(7f, 6f, 3.5f, 6f, 3.5f, 8f); arc(6.5f, 8f, 3f, 90f, 90f)
                line(17f, 6f, 20.5f, 6f, 20.5f, 8f); arc(17.5f, 8f, 3f, 0f, 90f)
                line(12f, 15f, 12f, 18f); line(8f, 20.5f, 16f, 20.5f); line(9f, 18f, 15f, 18f)
            }
            Icon.MORE -> { dot(12f, 5.5f, 1.7f); dot(12f, 12f, 1.7f); dot(12f, 18.5f, 1.7f) }
        }
    }

    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { stroke.colorFilter = colorFilter; fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
