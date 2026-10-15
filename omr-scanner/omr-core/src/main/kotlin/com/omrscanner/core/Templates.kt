package com.omrscanner.core

import kotlin.math.abs
import kotlin.math.min

/** How filled bubbles are told apart from print. */
enum class InkMode {
    /** Plain brightness: right for sheets printed in black or grey. */
    LUMINANCE,

    /**
     * The red channel: pink or red print, including letters inside the bubbles, almost vanishes while
     * black and blue pen stay dark. Right for Bangladeshi exam sheets printed in pink.
     */
    BLACK_INK,
}

/** A group of bubbles aligned together; [rows] share a line, and rows are stacked down the page. */
class BubbleBlock(val rows: List<List<Pt>>, val radius: Double) {
    /** Free paper between neighbouring circles, sideways and up/down (mm, half the clear space). */
    val freeX: Double
    val freeY: Double

    init {
        var dx = Double.MAX_VALUE
        for (row in rows) for (i in 1 until row.size) dx = min(dx, abs(row[i].x - row[i - 1].x))
        var dy = Double.MAX_VALUE
        for (i in 1 until rows.size) dy = min(dy, abs(rows[i][0].y - rows[i - 1][0].y))
        freeX = if (dx == Double.MAX_VALUE) 1.0 else (dx - 2 * radius) / 2
        freeY = if (dy == Double.MAX_VALUE) 1.0 else (dy - 2 * radius) / 2
    }
}

/** A column-per-digit field such as a roll number: [columns][value] centres, values labelled by [labels]. */
class DigitField(val name: String, val columns: List<List<Pt>>, val radius: Double, val labels: List<String>) {
    /** Rows of the field (one per value) for alignment; columns may be shorter than [labels]. */
    val block: BubbleBlock
        get() = BubbleBlock(labels.indices.map { v -> columns.filter { it.size > v }.map { it[v] } }.filter { it.isNotEmpty() }, radius)
}

/** Everything the reader needs to know about one printed OMR sheet design. Units are millimetres. */
class SheetTemplate(
    val id: String,
    val name: String,
    /** Corner marker centres clockwise from the sheet's top-left. */
    val markerCenters: List<Pt>,
    val markerSize: Double,
    /** Question -> option centres. */
    val questions: List<List<Pt>>,
    val answerRadius: Double,
    /** How questions are grouped into printed columns, as [first, last) index ranges. */
    val answerColumns: List<IntRange>,
    val fields: List<DigitField>,
    /** Name of the field holding the candidate's roll or ID number. */
    val idField: String?,
    /** Name of the field holding the question-set code, if the sheet has one. */
    val setField: String?,
    /** Points that are solid black only when the sheet is the right way up (empty if none). */
    val orientationMarks: List<Pt>,
    val inkMode: InkMode,
    /** True when the app can print this design itself. */
    val printable: Boolean,
    /** Default letters for the options. */
    val optionLabels: List<String>,
) {
    val maxQuestions get() = questions.size
    val maxOptions get() = questions.maxOf { it.size }

    fun field(name: String?) = fields.firstOrNull { it.name == name }

    companion object {
        val DIGITS = List(10) { it.toString() }
        val BANGLA_DIGITS = listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯")

        val OMR_SCANNER: SheetTemplate = with(SheetLayout) {
            SheetTemplate(
                id = "omr-scanner",
                name = "OMR Scanner sheet (printed from this app)",
                markerCenters = markerCenters,
                markerSize = MARKER_SIZE,
                questions = List(MAX_QUESTIONS) { q -> List(MAX_OPTIONS) { answerBubble(q, it) } },
                answerRadius = ANSWER_RADIUS,
                answerColumns = (0 until MAX_QUESTIONS step QUESTIONS_PER_COLUMN).map { it until it + QUESTIONS_PER_COLUMN },
                fields = listOf(DigitField("Roll", List(ROLL_DIGITS) { p -> List(10) { rollBubble(p, it) } }, ROLL_RADIUS, DIGITS)),
                idField = "Roll",
                setField = null,
                orientationMarks = List(5) { Pt(BAR_LEFT + 2.0 + it * (BAR_RIGHT - BAR_LEFT - 4.0) / 4, (BAR_TOP + BAR_BOTTOM) / 2) },
                inkMode = InkMode.LUMINANCE,
                printable = true,
                optionLabels = SheetSpec.LATIN_LABELS,
            )
        }

        /** Bangladesh Public Service Commission preliminary test sheet (200 questions, ক–ঘ). */
        val BCS_200: SheetTemplate = run {
            val rowY = List(40) { 106.72 + it * 4.3659 }
            val blockX = listOf(24.0, 62.23, 100.52, 138.56, 176.70)
            val questions = blockX.flatMap { x0 -> rowY.map { y -> List(4) { o -> Pt(x0 + o * 6.52, y) } } }
            val digitY = List(10) { 41.87 + it * 3.322 }
            fun field(name: String, xs: List<Double>) = DigitField(name, xs.map { x -> digitY.map { Pt(x, it) } }, 1.6, BANGLA_DIGITS)
            SheetTemplate(
                id = "bcs-200",
                name = "BCS preliminary (200 questions)",
                markerCenters = listOf(Pt(10.63, 88.27), Pt(199.31, 88.27), Pt(199.31, 284.82), Pt(10.58, 284.82)),
                markerSize = 7.1,
                questions = questions,
                answerRadius = 1.6,
                answerColumns = (0 until 200 step 40).map { it until it + 40 },
                fields = listOf(
                    field("Registration", List(8) { 51.82 + it * 6.343 }),
                    field("Set", listOf(107.18)),
                    field("BCS", listOf(18.67, 26.25)),
                    field("Centre", listOf(40.43)),
                ),
                idField = "Registration",
                setField = "Set",
                orientationMarks = emptyList(),
                inkMode = InkMode.BLACK_INK,
                printable = false,
                optionLabels = SheetSpec.BANGLA_LABELS,
            )
        }

        /** Education board SSC/HSC multiple-choice answer sheet (50 questions, ক–ঘ). */
        val SSC_50: SheetTemplate = run {
            val rowY = listOf(
                60.16, 68.79, 77.43, 86.06, 94.70, 103.34, 111.97, 120.61, 129.24, 137.88, 146.56, 155.19, 163.87,
                172.51, 181.14, 189.44, 198.12, 206.76, 215.43, 224.07, 232.71, 241.34, 249.85, 258.49, 267.17,
            )
            val questions = listOf(listOf(33.74, 39.83, 45.93, 52.03), listOf(73.32, 79.42, 85.51, 91.61))
                .flatMap { xs -> rowY.map { y -> xs.map { Pt(it, y) } } }
            val rollY = listOf(65.87, 71.75, 77.60, 83.61, 89.53, 95.42, 101.26, 107.06, 113.16, 118.66)
            val regY = listOf(144.95, 150.83, 156.68, 162.69, 168.61, 174.50, 180.38, 186.14, 192.24, 197.74)
            fun cols(xs: List<Double>, ys: List<Double>) = xs.map { x -> ys.map { Pt(x, it) } }
            SheetTemplate(
                id = "ssc-50",
                name = "SSC/HSC board (50 questions)",
                markerCenters = listOf(Pt(15.83, 17.02), Pt(193.46, 17.02), Pt(193.46, 279.91), Pt(15.83, 279.91)),
                markerSize = 4.83,
                questions = questions,
                answerRadius = 1.7,
                answerColumns = listOf(0 until 25, 25 until 50),
                fields = listOf(
                    DigitField("Roll", cols(listOf(108.33, 113.83, 119.33, 124.84, 130.34, 135.85), rollY), 1.7, BANGLA_DIGITS),
                    DigitField("Set", listOf(rollY.take(4).map { Pt(156.44, it) }), 1.7, SheetSpec.BANGLA_LABELS.take(4)),
                    DigitField("Registration", cols(List(10) { 108.25 + it * 5.512 }, regY), 1.7, BANGLA_DIGITS),
                    DigitField("Subject code", listOf(regY.take(4).map { Pt(174.29, it) }) + cols(listOf(179.79, 185.30), regY), 1.7, BANGLA_DIGITS),
                ),
                idField = "Roll",
                setField = "Set",
                // The timing strip down the left edge.
                orientationMarks = List(9) { Pt(15.83, 37.59 + it * 6 * 4.5057) },
                inkMode = InkMode.BLACK_INK,
                printable = false,
                optionLabels = SheetSpec.BANGLA_LABELS,
            )
        }

        val ALL = listOf(OMR_SCANNER, BCS_200, SSC_50)

        fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: OMR_SCANNER
    }
}
