package com.omrscanner.core

enum class TextAlign { LEFT, CENTER, RIGHT }

/** Minimal drawing surface in millimetre units; implemented with AWT in tests and android.graphics in the app. */
interface SheetCanvas {
    fun fillRect(left: Double, top: Double, width: Double, height: Double, color: Int)
    fun strokeRect(left: Double, top: Double, width: Double, height: Double, stroke: Double, color: Int)
    fun fillCircle(cx: Double, cy: Double, r: Double, color: Int)
    fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Double, color: Int)
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Double, color: Int)

    /** Draws [text] with its baseline at [baseline]; [size] is the font size in millimetres. */
    fun text(text: String, x: Double, baseline: Double, size: Double, align: TextAlign, bold: Boolean, color: Int)
}

/** What to print on a blank sheet. Only the printed subset changes; bubble positions stay fixed. */
data class SheetSpec(
    val questionCount: Int = SheetLayout.MAX_QUESTIONS,
    val optionCount: Int = 4,
    val optionLabels: List<String> = LATIN_LABELS,
    val title: String = "OMR ANSWER SHEET",
    val examName: String = "",
) {
    init {
        require(questionCount in 1..SheetLayout.MAX_QUESTIONS) { "questionCount must be 1..${SheetLayout.MAX_QUESTIONS}" }
        require(optionCount in 2..SheetLayout.MAX_OPTIONS) { "optionCount must be 2..${SheetLayout.MAX_OPTIONS}" }
        require(optionLabels.size >= optionCount) { "need a label for every option" }
    }

    companion object {
        val LATIN_LABELS = listOf("A", "B", "C", "D", "E")
        val BANGLA_LABELS = listOf("ক", "খ", "গ", "ঘ", "ঙ")
    }
}

object SheetPainter {
    private const val INK = 0xFF000000.toInt()
    private const val TEXT = 0xFF222222.toInt()
    private const val MUTED = 0xFF666666.toInt()
    private const val BUBBLE_LABEL = 0xFF8C8C8C.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun paint(canvas: SheetCanvas, spec: SheetSpec) = with(SheetLayout) {
        canvas.fillRect(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT, WHITE)

        val half = MARKER_SIZE / 2
        for (c in markerCenters) canvas.fillRect(c.x - half, c.y - half, MARKER_SIZE, MARKER_SIZE, INK)
        canvas.fillRect(BAR_LEFT, BAR_TOP, BAR_RIGHT - BAR_LEFT, BAR_BOTTOM - BAR_TOP, INK)

        canvas.text(spec.title, PAGE_WIDTH / 2, 17.0, 6.0, TextAlign.CENTER, true, TEXT)

        paintDetails(canvas, spec)
        paintRoll(canvas)
        paintAnswers(canvas, spec)

        canvas.text(
            "Use a black or blue pen  •  Fill one bubble per question completely  •  Do not fold",
            PAGE_WIDTH / 2, 284.5, 2.5, TextAlign.CENTER, false, MUTED,
        )
    }

    private fun paintDetails(canvas: SheetCanvas, spec: SheetSpec) {
        val fields = listOf("Name", "Exam", "Class / Section", "Date")
        fields.forEachIndexed { i, label ->
            val y = 32.0 + i * 10.0
            canvas.text(label, 14.0, y, 3.4, TextAlign.LEFT, true, TEXT)
            canvas.line(44.0, y + 0.8, 130.0, y + 0.8, 0.25, MUTED)
        }
        if (spec.examName.isNotBlank()) canvas.text(spec.examName, 46.0, 42.0, 3.6, TextAlign.LEFT, false, TEXT)

        canvas.strokeRect(14.0, 70.0, 116.0, 23.0, 0.25, MUTED)
        canvas.text("INSTRUCTIONS", 17.0, 75.0, 2.8, TextAlign.LEFT, true, TEXT)
        val lines = listOf(
            "1. Write your roll number in the boxes and fill the matching bubbles.",
            "2. Fill exactly one bubble per question, completely and darkly.",
            "3. Do not make stray marks near the black corner squares.",
        )
        lines.forEachIndexed { i, s -> canvas.text(s, 17.0, 80.5 + i * 4.6, 2.6, TextAlign.LEFT, false, TEXT) }
    }

    private fun paintRoll(canvas: SheetCanvas) = with(SheetLayout) {
        canvas.strokeRect(138.0, 22.0, 54.0, 71.0, 0.25, MUTED)
        canvas.text("ROLL NUMBER", 165.0, 27.0, 3.0, TextAlign.CENTER, true, TEXT)
        for (pos in 0 until ROLL_DIGITS) {
            val x = ROLL_LEFT + pos * ROLL_COLUMN_PITCH
            canvas.strokeRect(x - 3.0, 29.5, 6.0, 6.5, 0.25, TEXT)
        }
        for (digit in 0..9) {
            val y = ROLL_TOP + digit * ROLL_ROW_PITCH
            canvas.text(digit.toString(), 144.5, y + 1.0, 2.8, TextAlign.CENTER, true, MUTED)
            for (pos in 0 until ROLL_DIGITS) {
                val c = rollBubble(pos, digit)
                canvas.strokeCircle(c.x, c.y, ROLL_RADIUS, 0.35, INK)
                canvas.text(digit.toString(), c.x, c.y + 0.85, 2.3, TextAlign.CENTER, false, BUBBLE_LABEL)
            }
        }
    }

    private fun paintAnswers(canvas: SheetCanvas, spec: SheetSpec) = with(SheetLayout) {
        canvas.text("ANSWERS", 14.0, 99.5, 3.0, TextAlign.LEFT, true, TEXT)
        canvas.line(36.0, 98.5, 196.0, 98.5, 0.25, MUTED)
        for (q in 0 until spec.questionCount) {
            val first = answerBubble(q, 0)
            val bold = (q + 1) % 5 == 0
            canvas.text("${q + 1}", first.x - 3.6, first.y + 1.1, 3.0, TextAlign.RIGHT, bold, TEXT)
            for (opt in 0 until spec.optionCount) {
                val c = answerBubble(q, opt)
                canvas.strokeCircle(c.x, c.y, ANSWER_RADIUS, 0.35, INK)
                canvas.text(spec.optionLabels[opt], c.x, c.y + 0.95, 2.6, TextAlign.CENTER, false, BUBBLE_LABEL)
            }
        }
    }
}
