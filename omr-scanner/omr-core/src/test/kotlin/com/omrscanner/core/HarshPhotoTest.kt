package com.omrscanner.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Low-resolution, blurry, noisy, bent and steeply angled photos. The reader may refuse a few of these,
 * but whenever it returns a result, every answer must be right or flagged for review, and the roll must be right.
 */
class HarshPhotoTest {
    @Test
    fun neverMisreadsSilently() {
        val trials = 30
        val misreads = ArrayList<String>()
        var refused = 0
        for (trial in 0 until trials) {
            val rng = Random(5000 + trial)
            val ink = Ink.entries[trial % 3]
            val answers = List(100) { if (rng.nextDouble() < 0.1) emptyList() else listOf(rng.nextInt(4)) }
            val sheet = FilledSheet(SheetSpec(100, 4), answers, List(6) { rng.nextInt(10) }, ink)
            val page = SyntheticSheets.render(sheet, 8.0, rng)
            val portrait = rng.nextBoolean()
            val (w, h) = listOf(1280 to 960, 1600 to 1200, 4000 to 3000).random(rng)
            val p = SyntheticSheets.PhotoParams(
                width = if (portrait) h else w, height = if (portrait) w else h,
                rotation = listOf(0.0, 90.0, 180.0, 270.0).random(rng) + rng.nextDouble(-20.0, 20.0),
                coverage = rng.nextDouble(0.5, 0.97), perspective = rng.nextDouble(0.04, 0.13),
                curl = rng.nextDouble(1.0, 3.0), shading = rng.nextDouble(0.3, 0.6), shadow = true,
                noise = rng.nextDouble(4.0, 14.0), blurRadius = rng.nextInt(1, 4), jpegQuality = 0.45f,
            )
            val label = "trial $trial ${p.width}x${p.height} coverage=%.2f perspective=%.2f curl=%.1f noise=%.0f blur=%d $ink"
                .format(p.coverage, p.perspective, p.curl, p.noise, p.blurRadius)
            val photo = SyntheticSheets.photograph(page, 8.0, p, rng)
            try {
                val r = OmrReader.read(SyntheticSheets.toArgb(photo), ReaderOptions(100, 4))
                val wrong = answers.indices.filter { answers[it] != r.answers[it].marked.sorted() }
                // A wrong reading is acceptable only if it is flagged for the user to check.
                val silent = wrong.filter { !r.answers[it].needsReview }
                val detail = wrong.joinToString { q ->
                    "Q${q + 1} expected ${answers[q]} got ${r.answers[q].marked} review=${r.answers[q].needsReview} " +
                        r.answers[q].scores.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }
                }
                when {
                    silent.isNotEmpty() || r.rollDigits != sheet.roll -> misreads.add("$label: $detail, roll ${r.roll}, threshold %.2f".format(r.threshold))
                    wrong.isNotEmpty() -> println("$label flagged for review: $detail")
                    else -> println("$label ok, threshold %.2f".format(r.threshold))
                }
            } catch (e: OmrException) {
                refused++
                println("$label refused: ${e.message}")
            }
        }
        misreads.forEach { println("MISREAD $it") }
        assertTrue(misreads.isEmpty(), "silent misreads:\n" + misreads.joinToString("\n"))
        assertTrue(refused <= 2, "refused $refused/$trials harsh photos")
    }
}
