package com.omrscanner.core

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End to end with templates learned from blank copies: fill a sheet using the hand-measured reference
 * layout, photograph it, and read it with the learned template only.
 */
class LearnedSheetTest {
    private val outDir = System.getProperty("sheetOut")?.let { File(it).apply { mkdirs() } }

    private fun copy(src: BufferedImage) = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { drawImage(src, 0, 0, null); dispose() } }

    private fun resource(name: String) = copy(ImageIO.read(javaClass.getResourceAsStream("/sheets/$name.png")!!))

    /** Paints the corner squares white, to make a sheet that has none. */
    private fun withoutSquares(img: BufferedImage, ref: SheetTemplate): BufferedImage {
        val out = copy(img)
        val s = out.width / 210.0
        val g = out.createGraphics()
        g.color = Color.WHITE
        for (c in ref.markerCenters) {
            val h = ref.markerSize / 2 + 0.8
            g.fillRect(((c.x - h) * s).toInt(), ((c.y - h) * s).toInt(), (2 * h * s).toInt() + 1, (2 * h * s).toInt() + 1)
        }
        g.dispose()
        return out
    }

    private fun check(
        label: String, blank: BufferedImage, ref: SheetTemplate, refId: String, n: Int, trials: Int, seed: Int,
        expectAlignment: Alignment, minRead: Int, darkBackground: Boolean = false,
    ) {
        val learned = TemplateLearner.learn(SyntheticSheets.toArgb(blank))
        assertTrue(learned.alignment == expectAlignment, "$label learned ${learned.alignment}")
        val t = learned.toTemplate("learned", label, "Roll", null, ref.optionLabels)
        val pxPerMm = blank.width / 210.0
        var read = 0
        val misreads = ArrayList<String>()
        for (trial in 0 until trials) {
            val rng = Random(seed + trial)
            val img = copy(blank)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.scale(pxPerMm, pxPerMm)
            val answers = List(n) { if (rng.nextInt(12) == 0) emptyList() else listOf(rng.nextInt(4)) }
            answers.forEachIndexed { q, a -> for (o in a) SyntheticSheets.scribble(g, ref.questions[q][o], ref.answerRadius, Ink.BLACK_PEN, rng) }
            val idField = ref.field(refId)!!
            val id = idField.columns.map { col -> rng.nextInt(col.size).also { SyntheticSheets.scribble(g, col[it], idField.radius, Ink.BLACK_PEN, rng) } }
            g.dispose()
            var p = SyntheticSheets.randomPhotoParams(rng)
            if (darkBackground) p = SyntheticSheets.PhotoParams(
                width = p.width, height = p.height, rotation = p.rotation, coverage = p.coverage.coerceAtMost(0.9),
                perspective = p.perspective, curl = p.curl, shading = p.shading.coerceAtMost(0.3), shadow = false,
                noise = p.noise, blurRadius = p.blurRadius, jpegQuality = p.jpegQuality,
                background = listOf(0x7A5A3C, 0x3A3A40, 0x202020, 0x55606B).random(rng),
            )
            val photo = SyntheticSheets.photograph(img, pxPerMm, p, rng)
            if (trial == 0) outDir?.let { ImageIO.write(photo, "jpg", File(it, "learned-$label.jpg")) }
            val desc = "$label trial $trial (rot %.0f, cov %.2f, persp %.2f)".format(p.rotation, p.coverage, p.perspective)
            try {
                val r = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(template = t, questionCount = n))
                val wrong = answers.indices.filter { answers[it] != r.answers[it].marked.sorted() && !r.answers[it].needsReview }
                val gotId = r.field("Roll")?.values
                if (wrong.isNotEmpty() || gotId != id) misreads.add("$desc: wrong ${wrong.map { it + 1 }} id $gotId vs $id")
                else { read++; println("$desc ok") }
            } catch (e: OmrException) {
                println("$desc refused: ${e.message}")
            }
        }
        misreads.forEach { println("MISREAD $it") }
        assertTrue(misreads.isEmpty(), misreads.joinToString("\n"))
        assertTrue(read >= minRead, "$label: only $read/$trials read")
    }

    @Test
    fun learnedBcsReadsPhotos() = check("bcs", resource("bcs-200"), SheetTemplate.BCS_200, "Registration", 200, 8, 3100, Alignment.MARKERS, 8)

    @Test
    fun learnedSscReadsPhotos() = check("ssc", resource("ssc-50"), SheetTemplate.SSC_50, "Roll", 50, 8, 3200, Alignment.MARKERS, 8)

    @Test
    fun bcsWithoutSquaresReadsByPageEdges() =
        check("bcs-nosquares", withoutSquares(resource("bcs-200"), SheetTemplate.BCS_200), SheetTemplate.BCS_200, "Registration", 200, 8, 3300, Alignment.PAGE_EDGES, 6, darkBackground = true)

    @Test
    fun ownSheetWithoutSquaresReadsByPageEdges() {
        val blank = SyntheticSheets.render(FilledSheet(SheetSpec(100, 4), emptyList(), emptyList()), 8.0, Random(0), handwriting = false)
        check("own-nosquares", withoutSquares(blank, SheetTemplate.OMR_SCANNER), SheetTemplate.OMR_SCANNER, "Roll", 100, 8, 3400, Alignment.PAGE_EDGES, 6, darkBackground = true)
    }
}
