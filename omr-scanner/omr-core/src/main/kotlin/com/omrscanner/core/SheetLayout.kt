package com.omrscanner.core

/** A point, either on the sheet (millimetres) or in an image (pixels). */
data class Pt(val x: Double, val y: Double)

/**
 * Geometry of the printable OMR sheet, in millimetres on an A4 page.
 *
 * Bubble positions never depend on how many questions or options are printed, so the
 * reader can decode any sheet drawn by [SheetPainter] without knowing how it was configured.
 */
object SheetLayout {
    const val PAGE_WIDTH = 210.0
    const val PAGE_HEIGHT = 297.0

    const val MAX_QUESTIONS = 100
    const val MAX_OPTIONS = 5
    const val QUESTIONS_PER_COLUMN = 25
    const val ROLL_DIGITS = 6

    const val MARKER_SIZE = 8.0

    /** Corner marker centres, clockwise: top-left, top-right, bottom-right, bottom-left. */
    val markerCenters = listOf(Pt(14.0, 14.0), Pt(196.0, 14.0), Pt(196.0, 283.0), Pt(14.0, 283.0))

    /** Bar beside the top-left marker that tells which way up the sheet is. */
    const val BAR_LEFT = 27.0
    const val BAR_RIGHT = 41.0
    const val BAR_TOP = 12.5
    const val BAR_BOTTOM = 15.5

    const val ANSWER_RADIUS = 2.3
    const val ANSWER_TOP = 106.0
    const val ANSWER_ROW_PITCH = 6.8
    const val ANSWER_OPTION_PITCH = 6.4
    const val ANSWER_FIRST_BUBBLE = 11.5
    val answerColumnLeft = doubleArrayOf(14.0, 60.0, 106.0, 152.0)

    const val ROLL_RADIUS = 2.0
    const val ROLL_LEFT = 152.0
    const val ROLL_TOP = 41.0
    const val ROLL_COLUMN_PITCH = 6.8
    const val ROLL_ROW_PITCH = 5.2

    fun answerBubble(question: Int, option: Int): Pt {
        val column = question / QUESTIONS_PER_COLUMN
        val row = question % QUESTIONS_PER_COLUMN
        return Pt(
            answerColumnLeft[column] + ANSWER_FIRST_BUBBLE + option * ANSWER_OPTION_PITCH,
            ANSWER_TOP + row * ANSWER_ROW_PITCH,
        )
    }

    fun rollBubble(position: Int, digit: Int) =
        Pt(ROLL_LEFT + position * ROLL_COLUMN_PITCH, ROLL_TOP + digit * ROLL_ROW_PITCH)
}
