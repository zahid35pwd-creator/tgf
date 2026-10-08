package com.omrscanner.core

import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OmrReaderTest {
    private val outDir = System.getProperty("sheetOut")?.let { File(it).apply { mkdirs() } }

    private fun randomSheet(rng: Random, spec: SheetSpec, ink: Ink = Ink.BLACK_PEN, blankRate: Double = 0.1): FilledSheet {
        val answers = List(spec.questionCount) {
            if (rng.nextDouble() < blankRate) emptyList() else listOf(rng.nextInt(spec.optionCount))
        }
        val roll = List(SheetLayout.ROLL_DIGITS) { rng.nextInt(10) }
        return FilledSheet(spec, answers, roll, ink)
    }

    private fun assertReads(sheet: FilledSheet, result: OmrResult, label: String) {
        val expected = sheet.answers.map { it.sorted() }
        val actual = result.answers.map { it.marked.sorted() }
        val diffs = expected.indices.filter { expected[it] != actual[it] }
            .map { "Q${it + 1}: expected ${expected[it]} got ${actual[it]} scores=${result.answers[it].scores.joinToString { s -> "%.2f".format(s) }}" }
        assertTrue(diffs.isEmpty(), "$label: ${diffs.size} wrong (threshold ${"%.2f".format(result.threshold)})\n" + diffs.joinToString("\n"))
        assertEquals(sheet.roll, result.rollDigits, "$label: roll")
    }

    @Test
    fun readsFlatScan() {
        val rng = Random(1)
        val sheet = randomSheet(rng, SheetSpec(questionCount = 100, optionCount = 4))
        val page = SyntheticSheets.render(sheet, 8.0, rng)
        outDir?.let { ImageIO.write(page, "png", File(it, "flat-scan.png")) }
        val result = OmrReader.read(SyntheticSheets.toArgb(page), ReaderOptions(100, 4))
        assertReads(sheet, result, "flat")
        outDir?.let { saveRectified(result, File(it, "flat-rectified.png")) }
    }

    @Test
    fun readsRandomPhotos() {
        val failures = ArrayList<String>()
        val trials = 24
        for (trial in 0 until trials) {
            val rng = Random(100 + trial)
            val opts = if (trial % 4 == 3) 5 else 4
            val n = listOf(100, 60, 40, 25, 80)[trial % 5]
            val ink = Ink.entries[trial % Ink.entries.size]
            val sheet = randomSheet(rng, SheetSpec(questionCount = n, optionCount = opts), ink)
            val page = SyntheticSheets.render(sheet, 8.0, rng)
            val params = SyntheticSheets.randomPhotoParams(rng)
            val photo = SyntheticSheets.photograph(page, 8.0, params, rng)
            if (trial < 4) outDir?.let { ImageIO.write(photo, "jpg", File(it, "photo-$trial.jpg")) }
            val label = "trial $trial (rot ${"%.0f".format(params.rotation)}, ${params.width}x${params.height}, cov ${"%.2f".format(params.coverage)}, " +
                "persp ${"%.2f".format(params.perspective)}, curl ${"%.1f".format(params.curl)}, noise ${"%.1f".format(params.noise)}, blur ${params.blurRadius}, $ink)"
            val start = System.nanoTime()
            try {
                val result = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(n, opts))
                assertReads(sheet, result, label)
                println("$label ok in ${(System.nanoTime() - start) / 1_000_000} ms, threshold ${"%.2f".format(result.threshold)}")
                if (trial < 4) outDir?.let { saveRectified(result, File(it, "photo-$trial-rectified.png")) }
            } catch (e: Throwable) {
                failures.add("$label: ${e.message}")
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size}/$trials photos misread:\n" + failures.joinToString("\n"))
    }

    @Test
    fun readsEveryOrientation() {
        for (rotation in listOf(0.0, 90.0, 180.0, 270.0)) {
            val rng = Random(rotation.toInt())
            val sheet = randomSheet(rng, SheetSpec(questionCount = 50))
            val page = SyntheticSheets.render(sheet, 8.0, rng)
            val photo = SyntheticSheets.photograph(page, 8.0, SyntheticSheets.PhotoParams(width = 1500, height = 2000, rotation = rotation), rng)
            assertReads(sheet, OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(50, 4)), "rotation $rotation")
        }
    }

    @Test
    fun blankSheetReadsBlank() {
        val rng = Random(7)
        val sheet = FilledSheet(SheetSpec(questionCount = 40), List(40) { emptyList() }, List(6) { null })
        val photo = SyntheticSheets.photograph(SyntheticSheets.render(sheet, 8.0, rng), 8.0, SyntheticSheets.PhotoParams(width = 1500, height = 2000), rng)
        val result = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(40, 4))
        assertTrue(result.answers.all { it.status == MarkStatus.BLANK })
        assertEquals("??????", result.roll)
    }

    @Test
    fun detectsMultipleMarksAndIgnoresErasures() {
        val rng = Random(11)
        val answers = List(30) { q ->
            when (q) {
                3 -> listOf(0, 2)
                4 -> listOf(1, 2, 3)
                else -> listOf(q % 4)
            }
        }
        val sheet = FilledSheet(SheetSpec(questionCount = 30), answers, List(6) { it }, erased = listOf(10 to 3, 11 to 0))
        val photo = SyntheticSheets.photograph(SyntheticSheets.render(sheet, 8.0, rng), 8.0, SyntheticSheets.PhotoParams(width = 1500, height = 2000, rotation = 4.0), rng)
        val result = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(30, 4))
        assertReads(sheet, result, "multi")
        assertEquals(MarkStatus.MULTIPLE, result.answers[3].status)
        assertEquals(MarkStatus.MULTIPLE, result.answers[4].status)
        assertEquals(10 % 4, result.answers[10].answer)
        assertEquals(11 % 4, result.answers[11].answer)
    }

    @Test
    fun rejectsPicturesWithoutASheet() {
        val rng = Random(3)
        val img = java.awt.image.BufferedImage(1200, 1600, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        repeat(40) {
            g.color = java.awt.Color(rng.nextInt(0xFFFFFF))
            g.fillRect(rng.nextInt(1200), rng.nextInt(1600), rng.nextInt(20, 120), rng.nextInt(20, 120))
        }
        g.dispose()
        assertFailsWith<OmrException> { OmrReader.read(SyntheticSheets.toArgb(img)) }
    }

    @Test
    fun writesPrintableSheets() {
        val dir = outDir ?: return
        for ((name, spec) in listOf(
            "omr-sheet-100q-ABCD" to SheetSpec(100, 4),
            "omr-sheet-100q-ABCDE" to SheetSpec(100, 5),
            "omr-sheet-50q-ABCD" to SheetSpec(50, 4),
        )) {
            val img = SyntheticSheets.render(FilledSheet(spec, emptyList(), emptyList()), 300 / 25.4, Random(0), handwriting = false)
            ImageIO.write(img, "png", File(dir, "$name.png"))
        }
    }

    private fun saveRectified(result: OmrResult, file: File) {
        val r = result.rectified ?: return
        val img = java.awt.image.BufferedImage(r.width, r.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, r.width, r.height, r.pixels, 0, r.width)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        val s = result.rectifiedPxPerMm
        g.stroke = java.awt.BasicStroke(2f)
        result.answers.forEach { a ->
            result.answerCenters[a.question].forEachIndexed { o, c ->
                g.color = if (o in a.marked) java.awt.Color.GREEN.darker() else java.awt.Color(0, 120, 255, 120)
                val rr = SheetLayout.ANSWER_RADIUS * s * 1.25
                g.draw(java.awt.geom.Ellipse2D.Double(c.x * s - rr, c.y * s - rr, 2 * rr, 2 * rr))
            }
        }
        g.dispose()
        ImageIO.write(img, "png", file)
    }
}
