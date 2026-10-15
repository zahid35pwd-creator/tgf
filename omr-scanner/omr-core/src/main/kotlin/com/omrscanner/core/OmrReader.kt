package com.omrscanner.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class OmrException(message: String) : Exception(message)

data class ReaderOptions(
    val template: SheetTemplate = SheetTemplate.OMR_SCANNER,
    /** Questions to read (the rest of the sheet is ignored). */
    val questionCount: Int = template.maxQuestions,
    val optionCount: Int = 4,
    /** Longest side the photo is reduced to before processing. */
    val workingMaxSide: Int = 1800,
    /** Resolution of [OmrResult.rectified]; 0 skips building it. */
    val rectifiedPxPerMm: Double = 5.0,
) {
    init {
        require(questionCount in 1..template.maxQuestions)
        require(optionCount in 2..template.maxOptions)
    }
}

enum class MarkStatus { BLANK, SINGLE, MULTIPLE }

class QuestionReading(
    val question: Int,
    /** Darkness of each option's bubble, 0 (empty) .. 1 (solid). */
    val scores: DoubleArray,
    /** Options judged filled, darkest first. */
    val marked: List<Int>,
    /** True when a bubble was close to the threshold or an erasure was ignored. */
    val needsReview: Boolean,
) {
    val status: MarkStatus get() = when (marked.size) { 0 -> MarkStatus.BLANK; 1 -> MarkStatus.SINGLE; else -> MarkStatus.MULTIPLE }
    val answer: Int? get() = marked.singleOrNull()
}

class FieldReading(
    val def: DigitField,
    /** The value filled in each column, or null where it is blank or has several bubbles filled. */
    val values: List<Int?>,
    /** Where each bubble was found, in sheet millimetres: [column][value]. */
    val centers: List<List<Pt>>,
    /** Darkness of each bubble: [column][value]. */
    val scores: List<DoubleArray>,
) {
    val name get() = def.name
    val text: String get() = values.joinToString("") { v -> v?.let { def.labels[it] } ?: "?" }

    /** ASCII form: digits as 0–9, letter options as A, B, … */
    val ascii: String get() = values.joinToString("") { v ->
        when {
            v == null -> "?"
            def.labels == SheetTemplate.DIGITS || def.labels == SheetTemplate.BANGLA_DIGITS -> v.toString()
            else -> ('A' + v).toString()
        }
    }
}

class OmrResult(
    val template: SheetTemplate,
    val answers: List<QuestionReading>,
    val fields: List<FieldReading>,
    val threshold: Double,
    /** The sheet resampled flat and upright, [rectifiedPxPerMm] pixels per millimetre. */
    val rectified: ArgbImage?,
    val rectifiedPxPerMm: Double,
    /** Where each answer bubble was found, in sheet millimetres: [question][option]. */
    val answerCenters: List<List<Pt>>,
    /** Corner marker centres in the input image, clockwise from the sheet's top-left. */
    val corners: List<Pt>,
) {
    fun field(name: String?) = fields.firstOrNull { it.name == name }

    /** The roll / ID number, '?' for unreadable digits. */
    val roll: String get() = field(template.idField)?.ascii ?: ""
    val rollDigits: List<Int?> get() = field(template.idField)?.values ?: emptyList()

    /** The set code's value index, or null if the sheet has none or it is unreadable. */
    val set: Int? get() = field(template.setField)?.values?.singleOrNull()
}

object OmrReader {

    fun read(image: ArgbImage, options: ReaderOptions = ReaderOptions()): OmrResult {
        val t = options.template
        val work = ImageOps.downscale(image, options.workingMaxSide)
        val dark = ImageOps.darkness(ImageOps.luminance(work))
        val ink = if (t.inkMode == InkMode.BLACK_INK) ImageOps.darkness(ImageOps.redChannel(work)) else dark
        val candidates = MarkerFinder.find(ink)
        if (candidates.size < 4) {
            throw OmrException("Could not find the 4 black corner squares. Keep the whole sheet in view, flat and evenly lit.")
        }
        val sheetToImage = locateSheet(t, dark, candidates)
            ?: throw OmrException("This does not look like a \"${t.name}\" sheet. Check the sheet type in settings and keep all 4 corner squares visible.")
        val s = Sampler(dark, sheetToImage)
        val inkSampler = Sampler(ink, sheetToImage)

        val answerCenters = ArrayList<List<Pt>>()
        for (range in t.answerColumns) {
            val qs = range.filter { it < options.questionCount }
            if (qs.isEmpty()) continue
            val rows = qs.map { q -> t.questions[q].take(options.optionCount) }
            answerCenters.addAll(refineBlock(s, BubbleBlock(rows, t.answerRadius)))
        }
        val fieldCenters = t.fields.map { f ->
            val block = f.block
            val refined = refineBlock(s, block)
            // Back from rows (one per value) to columns.
            val iters = IntArray(f.columns.size)
            val cols = f.columns.map { ArrayList<Pt>() }
            for (row in refined) {
                var k = 0
                for (c in f.columns.indices) if (f.columns[c].size > iters[c]) { cols[c].add(row[k++]); iters[c]++ }
            }
            cols
        }

        val answerScores = answerCenters.map { row -> DoubleArray(row.size) { inkSampler.fill(row[it], t.answerRadius) } }
        val fieldScores = t.fields.mapIndexed { i, f -> fieldCenters[i].map { col -> DoubleArray(col.size) { inkSampler.fill(col[it], f.radius) } } }

        val all = answerScores.flatMap { it.asList() }
        val threshold = otsu(all).coerceIn(0.28, 0.55)
        // A lighter, clearly-darkest bubble is still accepted if it stands well above empty-bubble noise.
        val empty = all.filter { it < threshold }
        val mean = empty.average()
        val sd = sqrt(empty.sumOf { (it - mean) * (it - mean) } / max(1, empty.size))
        val rescue = min(threshold, max(0.25, mean + 4 * sd))

        val answers = answerScores.mapIndexed { q, scores -> classify(q, scores, threshold, rescue) }
        val fields = readFields(t, fieldScores, fieldCenters)

        val scale = image.width.toDouble() / work.width
        val corners = t.markerCenters.map { sheetToImage.map(it).let { p -> Pt(p.x * scale, p.y * scale) } }
        val rectified = if (options.rectifiedPxPerMm > 0) ImageOps.rectify(work, sheetToImage, options.rectifiedPxPerMm) else null
        return OmrResult(t, answers, fields, threshold, rectified, options.rectifiedPxPerMm, answerCenters, corners)
    }

    // ---- Finding the sheet -------------------------------------------------------------------

    /** Picks the 4 candidates (and rotation) that best explain the printed layout. */
    private fun locateSheet(t: SheetTemplate, dark: FloatImage, all: List<MarkerCandidate>): Homography? {
        val cands = all.sortedByDescending { it.area }.take(40)
        val quads = ArrayList<Pair<Double, List<MarkerCandidate>>>()
        val n = cands.size
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) for (d in c + 1 until n) {
            val group = listOf(cands[a], cands[b], cands[c], cands[d])
            val areas = group.map { it.area }
            if (areas.max() > 4.0 * areas.min()) continue
            val ordered = clockwise(group) ?: continue
            quads.add(Polygons.area(ordered.map { it.center }) to ordered)
        }
        // The real markers usually span the largest quadrilateral; larger ones may come from clutter around it.
        quads.sortByDescending { it.first }

        val fieldBlocks = t.fields.map { it.block }
        var best: Homography? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for ((_, group) in quads.take(24)) {
            for (rot in 0 until 4) {
                val markers = List(4) { group[(it + rot) % 4] }
                val h = Homography.fromPoints(t.markerCenters, markers.map { it.center }) ?: continue
                val s = Sampler(dark, h)
                if (!markerSizesFit(t, h, markers) || s.marginDarkness(t) > 0.25) continue
                val marks = if (t.orientationMarks.isEmpty()) 0.0 else t.orientationMarks.sumOf { s.at(it.x, it.y).toDouble() } / t.orientationMarks.size
                if (t.orientationMarks.isNotEmpty() && marks < 0.45) continue
                val ring = s.layoutScore(fieldBlocks)
                if (ring < 0.03) continue
                val score = marks + 2 * ring
                if (score > bestScore) { bestScore = score; best = h }
            }
        }
        return best
    }

    /** Each blob must be about as large as a printed marker would appear under [h]. */
    private fun markerSizesFit(t: SheetTemplate, h: Homography, markers: List<MarkerCandidate>): Boolean {
        val half = t.markerSize / 2
        return markers.indices.all { i ->
            val c = t.markerCenters[i]
            val square = listOf(Pt(c.x - half, c.y - half), Pt(c.x + half, c.y - half), Pt(c.x + half, c.y + half), Pt(c.x - half, c.y + half))
            val expected = Polygons.area(square.map { h.map(it) })
            markers[i].area / expected in 0.35..3.0
        }
    }

    /** Orders 4 candidates clockwise on screen (y down); null unless they form a convex quadrilateral. */
    private fun clockwise(p: List<MarkerCandidate>): List<MarkerCandidate>? {
        val cx = p.sumOf { it.center.x } / 4
        val cy = p.sumOf { it.center.y } / 4
        val o = p.sortedBy { atan2(it.center.y - cy, it.center.x - cx) }
        for (i in 0 until 4) {
            if (Polygons.cross(o[i].center, o[(i + 1) % 4].center, o[(i + 2) % 4].center) <= 0) return null
        }
        return o
    }

    // ---- Local alignment ---------------------------------------------------------------------

    /**
     * Paper is rarely perfectly flat, so after the corner-based mapping the block, then each row, is nudged
     * to where the printed circles actually are.
     */
    private fun refineBlock(s: Sampler, b: BubbleBlock): List<List<Pt>> {
        val rows = b.rows
        val sample = rows.flatten().let { all -> all.filterIndexed { i, _ -> i % max(1, all.size / 48) == 0 } }
        var block = bestOffset(s, sample, b, Pt(0.0, 0.0), 2.0, 0.5)
        block = bestOffset(s, sample, b, block, 0.5, 0.25)

        val offsets = rows.map { bestOffset(s, it, b, block, 1.0, 0.25) }
        val smooth = offsets.indices.map { i ->
            val win = offsets.subList(max(0, i - 2), min(offsets.size, i + 3))
            Pt(median(win.map { it.x }), median(win.map { it.y }))
        }
        return rows.mapIndexed { i, row -> row.map { Pt(it.x + smooth[i].x, it.y + smooth[i].y) } }
    }

    private fun bestOffset(s: Sampler, bubbles: List<Pt>, b: BubbleBlock, around: Pt, range: Double, step: Double): Pt {
        var best = around
        var bestScore = Double.NEGATIVE_INFINITY
        val steps = (range / step).toInt()
        for (iy in -steps..steps) for (ix in -steps..steps) {
            val dx = around.x + ix * step
            val dy = around.y + iy * step
            var sum = 0.0
            for (p in bubbles) sum += s.ringContrast(p.x + dx, p.y + dy, b)
            // Small pull towards the starting point so blank areas don't drift.
            val score = sum / bubbles.size - 0.01 * (abs(ix) + abs(iy))
            if (score > bestScore) { bestScore = score; best = Pt(dx, dy) }
        }
        return best
    }

    // ---- Classification ----------------------------------------------------------------------

    /**
     * Digit fields often print their digits inside the bubbles in black (e.g. BCS), which looks like ink.
     * Each digit's glyph is the same in every column, so its typical empty darkness (the median over
     * columns) is subtracted, and each column takes the bubble that clearly stands out.
     */
    private fun readFields(t: SheetTemplate, scores: List<List<DoubleArray>>, centers: List<List<List<Pt>>>): List<FieldReading> {
        val baseline = HashMap<Pair<List<String>, Int>, Double>()
        for ((labels, group) in t.fields.indices.groupBy { t.fields[it].labels }) {
            for (v in labels.indices) {
                val values = group.flatMap { i -> scores[i].filter { it.size > v }.map { it[v] } }
                // A per-digit baseline needs enough columns that most of them are unmarked.
                if (values.size >= 3) baseline[labels to v] = median(values)
            }
        }
        return t.fields.mapIndexed { i, f ->
            val values = scores[i].map { col ->
                val own = median(col.asList())
                val adj = DoubleArray(col.size) { v -> col[v] - (baseline[f.labels to v] ?: own) }
                val order = adj.indices.sortedByDescending { adj[it] }
                val top = adj[order[0]]
                val second = if (order.size > 1) adj[order[1]] else 0.0
                when {
                    top < FIELD_MIN_CONTRAST -> null
                    second >= max(FIELD_MIN_CONTRAST, 0.6 * top) -> null // several filled
                    else -> order[0]
                }
            }
            FieldReading(f, values, centers[i], scores[i])
        }
    }

    private const val FIELD_MIN_CONTRAST = 0.1

    private fun classify(question: Int, scores: DoubleArray, t: Double, rescue: Double): QuestionReading {
        val order = scores.indices.sortedByDescending { scores[it] }
        var marked = order.filter { scores[it] >= t }
        var review = scores.any { abs(it - t) < 0.08 }
        if (marked.isEmpty() && scores[order[0]] >= rescue && scores[order[0]] - scores[order[1]] >= 0.12) {
            marked = order.take(1)
            review = true
        }
        if (marked.size >= 2 && scores[order[1]] < 0.6 * scores[order[0]]) {
            // One clear fill plus a much lighter mark: most likely an erasure.
            marked = marked.take(1)
            review = true
        }
        return QuestionReading(question, scores, marked, review)
    }

    /** Otsu's threshold over bubble darkness values. */
    private fun otsu(values: List<Double>): Double {
        val bins = 100
        val hist = IntArray(bins)
        for (v in values) hist[(v * bins).toInt().coerceIn(0, bins - 1)]++
        val total = values.size.toDouble()
        val sumAll = (0 until bins).sumOf { it * hist[it].toDouble() }
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var first = 0
        var last = 0
        for (i in 0 until bins) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best * (1 + 1e-9)) { best = between; first = i; last = i }
            else if (between >= best * (1 - 1e-9) && last == i - 1) last = i
        }
        // Every cut inside an empty gap scores the same; take the middle of the gap.
        return ((first + last) / 2.0 + 1.0) / bins
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/** Samples the darkness image at sheet (millimetre) coordinates. */
internal class Sampler(private val dark: FloatImage, h: Homography) {
    private val m = h.m

    fun at(x: Double, y: Double): Float {
        val d = m[6] * x + m[7] * y + m[8]
        return dark.sample((m[0] * x + m[1] * y + m[2]) / d, (m[3] * x + m[4] * y + m[5]) / d)
    }

    /**
     * How strongly a printed circle shows at (x, y): ring darkness minus the paper just outside it.
     * Directions where the neighbouring circle is too close to leave clear paper are skipped.
     */
    fun ringContrast(x: Double, y: Double, b: BubbleBlock): Double {
        val r = b.radius
        var ring = 0.0
        var gap = 0.0
        var n = 0
        for (k in 0 until RAYS) {
            val c = COS[k]
            val s = SIN[k]
            val free = min(0.75, if (abs(c) >= abs(s)) b.freeX else b.freeY)
            if (free < 0.3) continue
            val band = min(0.45, free * 0.6)
            var best = 0f
            var t = r - 0.45
            while (t <= r + band + 1e-3) {
                best = max(best, at(x + c * t, y + s * t))
                t += 0.225
            }
            ring += best
            gap += at(x + c * (r + free), y + s * (r + free))
            n++
        }
        return if (n == 0) 0.0 else (ring - gap) / n
    }

    /** Mean darkness inside the bubble, keeping clear of the printed outline. */
    fun fill(c: Pt, r: Double): Double {
        val inner = 0.6 * r
        val step = inner / 6
        var sum = 0.0
        var n = 0
        for (iy in -6..6) for (ix in -6..6) {
            val dx = ix * step
            val dy = iy * step
            if (dx * dx + dy * dy > inner * inner) continue
            sum += at(c.x + dx, c.y + dy)
            n++
        }
        return sum / n
    }

    /** Average ring contrast over the sheet's ID fields, which are printed on every copy. */
    fun layoutScore(blocks: List<BubbleBlock>): Double {
        var sum = 0.0
        var n = 0
        for (b in blocks) for (row in b.rows) for (p in row) {
            sum += ringContrast(p.x, p.y, b)
            n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** Darkness of the blank paper around the corner markers (should be near 0). */
    fun marginDarkness(t: SheetTemplate): Double {
        val d = t.markerSize / 2 + 3.5
        var sum = 0.0
        var n = 0
        for (c in t.markerCenters) {
            for ((dx, dy) in listOf(d to 0.0, -d to 0.0, 0.0 to d, 0.0 to -d)) { sum += at(c.x + dx, c.y + dy); n++ }
        }
        return sum / n
    }

    private companion object {
        const val RAYS = 16
        val COS = DoubleArray(RAYS) { cos(2 * PI * it / RAYS) }
        val SIN = DoubleArray(RAYS) { sin(2 * PI * it / RAYS) }
    }
}
