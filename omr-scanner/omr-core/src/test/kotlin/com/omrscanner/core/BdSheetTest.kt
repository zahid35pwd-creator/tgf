package com.omrscanner.core

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Real Bangladeshi sheets (rendered from the published PDFs), filled in and photographed. */
class BdSheetTest {
    private val outDir = System.getProperty("sheetOut")?.let { File(it).apply { mkdirs() } }

    private fun page(name: String): BufferedImage {
        val src = ImageIO.read(javaClass.getResourceAsStream("/sheets/$name.png")!!)
        val img = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { drawImage(src, 0, 0, null); dispose() }
        return img
    }

    private class Filled(val page: BufferedImage, val answers: List<List<Int>>, val fields: Map<String, List<Int?>>)

    private fun fill(t: SheetTemplate, name: String, n: Int, ink: Ink, rng: Random): Filled {
        val img = page(name)
        val pxPerMm = img.width / 210.0
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.scale(pxPerMm, pxPerMm)
        val answers = List(n) { q ->
            when (rng.nextInt(20)) {
                0 -> emptyList()
                1 -> listOf(0, 3)
                else -> listOf(rng.nextInt(4))
            }
        }
        answers.forEachIndexed { q, opts -> for (o in opts) SyntheticSheets.scribble(g, t.questions[q][o], t.answerRadius, ink, rng) }
        val fields = t.fields.associate { f ->
            f.name to f.columns.map { col -> rng.nextInt(col.size).also { v -> SyntheticSheets.scribble(g, col[v], f.radius, ink, rng) } as Int? }
        }
        g.dispose()
        return Filled(img, answers, fields)
    }

    private fun check(t: SheetTemplate, name: String, trials: Int, seed: Int) {
        val failures = ArrayList<String>()
        for (trial in 0 until trials) {
            val rng = Random(seed + trial)
            val ink = if (trial % 3 == 2) Ink.BLUE_PEN else Ink.BLACK_PEN
            val sheet = fill(t, name, t.maxQuestions, ink, rng)
            val params = if (trial == 0) SyntheticSheets.PhotoParams(width = 2448, height = 3264, rotation = 2.0, perspective = 0.02)
            else SyntheticSheets.randomPhotoParams(rng)
            val photo = SyntheticSheets.photograph(sheet.page, sheet.page.width / 210.0, params, rng)
            if (trial < 2) outDir?.let { ImageIO.write(photo, "jpg", File(it, "${t.id}-$trial.jpg")) }
            val label = "${t.id} trial $trial (rot %.0f, ${params.width}x${params.height}, cov %.2f, persp %.2f, curl %.1f, noise %.1f, blur ${params.blurRadius}, $ink)"
                .format(params.rotation, params.coverage, params.perspective, params.curl, params.noise)
            try {
                val r = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(template = t, questionCount = t.maxQuestions, optionCount = 4))
                val wrong = sheet.answers.indices.filter { sheet.answers[it].sorted() != r.answers[it].marked.sorted() }
                val badFields = t.fields.filter { sheet.fields[it.name] != r.field(it.name)!!.values }
                    .map { "${it.name} expected ${sheet.fields[it.name]} got ${r.field(it.name)!!.values} " + r.field(it.name)!!.scores.joinToString { c -> c.joinToString(" ", "[", "]") { v -> "%.2f".format(v) } } }
                if (wrong.isNotEmpty() || badFields.isNotEmpty()) {
                    failures.add("$label: ${wrong.size} wrong ${wrong.take(5).map { q -> "Q${q + 1} ${sheet.answers[q]}->${r.answers[q].marked} " + r.answers[q].scores.joinToString { "%.2f".format(it) } }} $badFields threshold %.2f".format(r.threshold))
                } else println("$label ok, threshold %.2f".format(r.threshold))
            } catch (e: OmrException) {
                failures.add("$label: ${e.message}")
            }
        }
        failures.forEach { println("FAIL $it") }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun readsBcsSheets() = check(SheetTemplate.BCS_200, "bcs-200", 12, 700)

    @Test
    fun readsSscSheets() = check(SheetTemplate.SSC_50, "ssc-50", 12, 900)

    @Test
    fun blankBoardSheetReadsBlank() {
        val rng = Random(5)
        val t = SheetTemplate.SSC_50
        val photo = SyntheticSheets.photograph(page("ssc-50"), page("ssc-50").width / 210.0, SyntheticSheets.PhotoParams(width = 1500, height = 2000), rng)
        val r = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(template = t, questionCount = 50))
        assertTrue(r.answers.all { it.status == MarkStatus.BLANK }, "pink print must not count as marks: " +
            r.answers.filter { it.status != MarkStatus.BLANK }.map { it.question + 1 })
        assertEquals("??????", r.roll)
    }

    @Test
    fun refusesTheWrongSheetType() {
        val rng = Random(9)
        val img = page("bcs-200")
        val photo = SyntheticSheets.photograph(img, img.width / 210.0, SyntheticSheets.PhotoParams(width = 1500, height = 2000), rng)
        assertFailsWith<OmrException> { OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(template = SheetTemplate.SSC_50, questionCount = 50)) }
        assertFailsWith<OmrException> { OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(template = SheetTemplate.OMR_SCANNER, questionCount = 50)) }
    }
}
