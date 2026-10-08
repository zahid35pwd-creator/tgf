package com.omrscanner.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.omrscanner.core.GradeResult
import com.omrscanner.core.OmrResult
import com.omrscanner.core.Pt
import com.omrscanner.core.SheetLayout
import com.omrscanner.core.Verdict

/** Draws the reading on top of the flattened sheet image. */
object ResultImage {
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

        for (a in result.answers) {
            val centers = result.answerCenters[a.question]
            val g = grade?.questions?.getOrNull(a.question)
            val key = g?.key
            when (g?.verdict ?: Verdict.UNKEYED) {
                Verdict.CORRECT -> ring(centers[a.marked[0]], SheetLayout.ANSWER_RADIUS, Ui.GREEN)
                Verdict.WRONG -> {
                    ring(centers[a.marked[0]], SheetLayout.ANSWER_RADIUS, Ui.RED)
                    key?.let { ring(centers[it], SheetLayout.ANSWER_RADIUS, Ui.GREEN) }
                }
                Verdict.MULTIPLE -> {
                    for (o in a.marked) ring(centers[o], SheetLayout.ANSWER_RADIUS, Ui.PURPLE)
                    key?.let { ring(centers[it], SheetLayout.ANSWER_RADIUS, Ui.GREEN) }
                }
                Verdict.BLANK -> key?.let { ring(centers[it], SheetLayout.ANSWER_RADIUS, Ui.ORANGE) }
                Verdict.UNKEYED -> for (o in a.marked) ring(centers[o], SheetLayout.ANSWER_RADIUS, Ui.BLUE)
            }
        }
        result.rollDigits.forEachIndexed { pos, d ->
            if (d != null) ring(result.rollCenters[pos][d], SheetLayout.ROLL_RADIUS, Ui.BLUE)
        }
        return bmp
    }
}
