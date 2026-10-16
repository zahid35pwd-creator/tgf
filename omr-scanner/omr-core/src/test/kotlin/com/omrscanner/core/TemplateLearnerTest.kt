package com.omrscanner.core

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Learning sheet layouts from blank copies must reproduce the hand-measured templates. */
class TemplateLearnerTest {
    private fun resource(name: String): BufferedImage {
        val src = ImageIO.read(javaClass.getResourceAsStream("/sheets/$name.png")!!)
        return BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { drawImage(src, 0, 0, null); dispose() } }
    }

    private fun ownBlank(): BufferedImage =
        SyntheticSheets.render(FilledSheet(SheetSpec(100, 4), emptyList(), emptyList()), 8.0, Random(0), handwriting = false)

    private fun grid(width: Int, height: Int): Pair<List<List<Pt>>, List<List<Pt>>> {
        // A rectangular grid of [width] columns × [height] rows at unit spacing, used only by classify.
        val rows = List(height) { r -> List(width) { c -> Pt(c * 1.0, r * 1.0) } }
        val cols = List(width) { c -> List(height) { r -> Pt(c * 1.0, r * 1.0) } }
        return rows to cols
    }

    @Test
    fun classifiesEveryKindOfField() {
        val learner = TemplateLearner
        fun check(width: Int, height: Int, expected: GroupKind) {
            val (rows, cols) = grid(width, height)
            val got = learner.classifyForTest(rows, cols)
            assertEquals(expected, got, "grid ${width}×${height} ($height rows × $width options)")
        }
        // ID / Roll / Registration — 10 rows per column, every width is a digit field.
        for (digits in 2..10) check(digits, 10, GroupKind.NUMBER)
        // Name A–Z grid.
        check(6, 26, GroupKind.NAME)
        check(10, 26, GroupKind.NAME)
        check(4, 26, GroupKind.NAME)
        // Short choice codes (set).
        check(4, 1, GroupKind.LETTERS)
        check(1, 4, GroupKind.LETTERS)
        // Answer grids: many rows of 2–5 options.
        check(4, 25, GroupKind.ANSWERS)
        check(5, 20, GroupKind.ANSWERS)
        check(4, 50, GroupKind.ANSWERS)
    }

    private fun learn(img: BufferedImage, label: String): LearnedSheet {
        val l = TemplateLearner.learn(SyntheticSheets.toArgb(img))
        println("$label: ${l.alignment}, page ${l.pageWidth}x${l.pageHeight}, ink ${l.inkMode}, markers ${l.markerCenters}, marks ${l.orientationMarks.size}")
        for (g in l.groups) println("  ${g.kind} '${g.name}' at (%.1f, %.1f): ${g.describe()}, r=%.2f".format(g.left, g.top, g.radius) +
            if (g.rows.size < 12) " rows y=" + g.rows.joinToString { "%.1f".format(it.first().y) } else "")
        return l
    }

    /** Every question's options must be within [tol] mm of the reference template's. */
    private fun assertSameQuestions(expected: SheetTemplate, actual: SheetTemplate, n: Int, tol: Double = 0.6) {
        assertTrue(actual.maxQuestions >= n, "learned ${actual.maxQuestions} questions, expected at least $n")
        val short = (0 until n).filter { actual.questions[it].size < 4 }
        assertTrue(short.isEmpty(), "questions with missing options: " + short.map { "Q${it + 1} ${actual.questions[it]}" })
        for (q in 0 until n) for (o in 0 until 4) {
            val e = expected.questions[q][o]; val a = actual.questions[q][o]
            assertTrue(hypot(e.x - a.x, e.y - a.y) < tol, "Q${q + 1} option $o: expected $e got $a")
        }
    }

    private fun assertSameField(expected: DigitField, actual: DigitField, tol: Double = 0.6) {
        assertEquals(expected.columns.size, actual.columns.size, "${expected.name} columns")
        expected.columns.forEachIndexed { c, col -> col.forEachIndexed { v, e ->
            val a = actual.columns[c][v]
            assertTrue(hypot(e.x - a.x, e.y - a.y) < tol, "${expected.name} column $c value $v: expected $e got $a")
        } }
    }

    @Test
    fun learnsBcs() {
        val l = learn(resource("bcs-200"), "BCS")
        assertEquals(Alignment.MARKERS, l.alignment)
        assertEquals(InkMode.RED, l.inkMode)
        val t = l.toTemplate("x", "BCS", "Roll", null, SheetSpec.BANGLA_LABELS)
        assertEquals(200, t.maxQuestions)
        assertSameQuestions(SheetTemplate.BCS_200, t, 200)
        assertSameField(SheetTemplate.BCS_200.field("Registration")!!, t.field("Roll")!!)
        SheetTemplate.BCS_200.markerCenters.zip(t.markerCenters).forEach { (e, a) -> assertTrue(hypot(e.x - a.x, e.y - a.y) < 0.5, "marker $e vs $a") }
    }

    @Test
    fun learnsSsc() {
        val l = learn(resource("ssc-50"), "SSC")
        assertEquals(Alignment.MARKERS, l.alignment)
        val t = l.toTemplate("x", "SSC", "Roll", "Set", SheetSpec.BANGLA_LABELS)
        assertEquals(50, t.maxQuestions)
        assertSameQuestions(SheetTemplate.SSC_50, t, 50)
        assertSameField(SheetTemplate.SSC_50.field("Roll")!!, t.field("Roll")!!)
        assertSameField(SheetTemplate.SSC_50.field("Set")!!, t.field("Set")!!)
    }

    @Test
    fun learnsOwnSheet() {
        val l = learn(ownBlank(), "own")
        assertEquals(InkMode.LUMINANCE, l.inkMode)
        val t = l.toTemplate("x", "own", "Roll", null, SheetSpec.LATIN_LABELS)
        assertEquals(100, t.maxQuestions)
        assertSameQuestions(SheetTemplate.OMR_SCANNER, t, 100)
        assertSameField(SheetTemplate.OMR_SCANNER.field("Roll")!!, t.field("Roll")!!)
    }

    @Test
    fun learnedTemplateSurvivesSaving() {
        val t = TemplateLearner.learn(SyntheticSheets.toArgb(resource("ssc-50"))).toTemplate("custom-1", "My \"SSC\" sheet", "Roll", "Set", SheetSpec.BANGLA_LABELS)
        val back = TemplateCodec.decode(TemplateCodec.encode(t))
        assertEquals(t.name, back.name)
        assertEquals(t.inkMode, back.inkMode)
        assertEquals(t.alignment, back.alignment)
        assertEquals(t.answerBlocks, back.answerBlocks)
        assertEquals(t.fields.map { it.name to it.labels }, back.fields.map { it.name to it.labels })
        assertEquals("Roll", back.idField)
        assertEquals("Set", back.setField)
        assertSameQuestions(t, back, 50, 0.01)
        assertSameField(t.field("Roll")!!, back.field("Roll")!!, 0.01)
    }

    @Test
    fun learnsSchoolSheet() {
        val l = learn(resource("school-30"), "SCHOOL")
        assertEquals(Alignment.PAGE_EDGES, l.alignment)
        val t = l.toTemplate("x", "school", "Roll", "Set", SheetSpec.BANGLA_LABELS)
        assertEquals(30, t.maxQuestions)
        assertTrue(t.questions.all { it.size == 4 }, "4 options each")
        assertEquals(6, t.field("Roll")!!.columns.size)
        assertEquals(listOf(10, 3), l.groups.filter { it.kind == GroupKind.NUMBER && it.name != "Roll" }.map { it.columns.size })
        assertEquals(4, t.field("Set")!!.columns.single().size, "set code ক খ গ ঘ printed across")
        assertEquals(5, l.groups.single { it.kind == GroupKind.LETTERS && it.name != "Set" }.columns.single().size, "class ৬ষ্ঠ–১০ম")
    }

    @Test
    fun learnsCuetSheet() {
        val l = learn(resource("cuet-50"), "CUET")
        assertEquals(Alignment.PAGE_EDGES, l.alignment)
        val t = l.toTemplate("x", "cuet", "Roll", null, SheetSpec.LATIN_LABELS)
        assertEquals(50, t.maxQuestions)
        assertTrue(t.questions.all { it.size == 4 }, "4 options each: " + t.questions.withIndex().filter { it.value.size != 4 }.map { "Q${it.index + 1} " + it.value.joinToString { p -> "(%.1f,%.1f)".format(p.x, p.y) } })
        val name = t.field("Name")!!
        assertEquals(24, name.columns.size, "name columns at " + name.columns.joinToString { "%.1f(${it.size})".format(it[0].x) })
        assertTrue(name.columns.all { it.size == 26 }, "A–Z in every name column")
        assertEquals(7, t.field("Roll")!!.columns.size)
        assertEquals(listOf(3, 3), l.groups.filter { it.kind == GroupKind.NUMBER && it.name != "Roll" }.map { it.columns.size })
    }
}
