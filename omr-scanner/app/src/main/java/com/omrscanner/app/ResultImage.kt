package com.omrscanner.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.omrscanner.core.GradeResult
import com.omrscanner.core.OmrResult
import com.omrscanner.core.Pt
import com.omrscanner.core.Verdict

/** Draws the reading on top of the flattened sheet image. */
object ResultImage {
    // Fixed colours: the marks sit on white paper whatever the app theme.
    private const val GREEN = 0xFF16A34A.toInt()
    private const val RED = 0xFFDC2626.toInt()
    private const val ORANGE = 0xFFEA7C0C.toInt()
    private const val PURPLE = 0xFF9333EA.toInt()
    private const val BLUE = 0xFF2563EB.toInt()

    fun render(result: OmrResult, grade: GradeResult?): Bitmap? {
        val img = result.rectified ?: return null
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(img.pixels, 0, img.width, 0, 0, img.width, img.height)
        val canvas = Canvas(bmp)
        val s = result.rectifiedPxPerMm
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (0.6 * s).toFloat()
        }

        fun ring(p: Pt, r: Double, color: Int) {
            paint.color = color
            canvas.drawCircle((p.x * s).toFloat(), (p.y * s).toFloat(), (r * 1.35 * s).toFloat(), paint)
        }

        val r0 = result.template.answerRadius
        for (a in result.answers) {
            val centers = result.answerCenters[a.question]
            val g = grade?.questions?.getOrNull(a.question)
            val key = g?.key
            when (g?.verdict ?: Verdict.UNKEYED) {
                Verdict.CORRECT -> ring(centers[a.marked[0]], r0, GREEN)
                Verdict.WRONG -> {
                    ring(centers[a.marked[0]], r0, RED)
                    key?.let { ring(centers[it], r0, GREEN) }
                }
                Verdict.MULTIPLE -> {
                    for (o in a.marked) ring(centers[o], r0, PURPLE)
                    key?.let { ring(centers[it], r0, GREEN) }
                }
                Verdict.BLANK -> key?.let { ring(centers[it], r0, ORANGE) }
                Verdict.UNKEYED -> for (o in a.marked) ring(centers[o], r0, BLUE)
            }
        }
        for (f in result.fields) {
            f.values.forEachIndexed { col, v -> if (v != null) ring(f.centers[col][v], f.def.radius, BLUE) }
        }
        return bmp
    }
}
