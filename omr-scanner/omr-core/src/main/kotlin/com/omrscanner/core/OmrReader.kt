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
    /** Questions to read (the rest of the sheet is ignored). */
    val questionCount: Int = SheetLayout.MAX_QUESTIONS,
    val optionCount: Int = 4,
    /** Longest side the photo is reduced to before processing. */
    val workingMaxSide: Int = 1800,
    /** Resolution of [OmrResult.rectified]; 0 skips building it. */
    val rectifiedPxPerMm: Double = 5.0,
) {
    init {
        require(questionCount in 1..SheetLayout.MAX_QUESTIONS)
        require(optionCount in 2..SheetLayout.MAX_OPTIONS)
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

class OmrResult(
    val answers: List<QuestionReading>,
    /** Each roll digit, or null where it is blank or has several bubbles filled. */
    val rollDigits: List<Int?>,
    val threshold: Double,
    /** The sheet resampled flat and upright, [rectifiedPxPerMm] pixels per millimetre. */
    val rectified: ArgbImage?,
    val rectifiedPxPerMm: Double,
    /** Where each answer bubble was found, in sheet millimetres: [question][option]. */
    val answerCenters: List<List<Pt>>,
    /** Where each roll bubble was found, in sheet millimetres: [position][digit]. */
    val rollCenters: List<List<Pt>>,
    /** Corner marker centres in the input image, clockwise from the sheet's top-left. */
    val corners: List<Pt>,
) {
    val roll: String get() = rollDigits.joinToString("") { it?.toString() ?: "?" }
}

object OmrReader {

    fun read(image: ArgbImage, options: ReaderOptions = ReaderOptions()): OmrResult {
        val work = ImageOps.downscale(image, options.workingMaxSide)
        val dark = ImageOps.darkness(ImageOps.luminance(work))
        val candidates = MarkerFinder.find(dark)
        if (candidates.size < 4) {
            throw OmrException("Could not find the 4 black corner squares. Keep the whole sheet in view, flat and evenly lit.")
        }
        val sheetToImage = locateSheet(dark, candidates)
            ?: throw OmrException("This does not look like an OMR Scanner sheet. Print sheets from the app and keep all 4 corner squares visible.")
        val s = Sampler(dark, sheetToImage)

        val answerCenters = refineAnswers(s, options)
        val rollCenters = refineRoll(s)

        val answerScores = answerCenters.map { row -> DoubleArray(row.size) { s.fill(row[it], SheetLayout.ANSWER_RADIUS) } }
        val rollScores = rollCenters.map { col -> DoubleArray(col.size) { s.fill(col[it], SheetLayout.ROLL_RADIUS) } }

        val all = answerScores.flatMap { it.asList() } + rollScores.flatMap { it.asList() }
        val threshold = otsu(all).coerceIn(0.28, 0.55)
        // A lighter, clearly-darkest bubble is still accepted if it stands well above empty-bubble noise.
        val empty = all.filter { it < threshold }
        val mean = empty.average()
        val sd = sqrt(empty.sumOf { (it - mean) * (it - mean) } / max(1, empty.size))
        val rescue = min(threshold, max(0.25, mean + 4 * sd))

        val answers = answerScores.mapIndexed { q, scores -> classify(q, scores, threshold, rescue) }
        val rollDigits = rollScores.map { scores -> classify(0, scores, threshold, rescue).answer }

        val scale = image.width.toDouble() / work.width
        val corners = SheetLayout.markerCenters.map { sheetToImage.map(it).let { p -> Pt(p.x * scale, p.y * scale) } }
        val rectified = if (options.rectifiedPxPerMm > 0) ImageOps.rectify(work, sheetToImage, options.rectifiedPxPerMm) else null
        return OmrResult(answers, rollDigits, threshold, rectified, options.rectifiedPxPerMm, answerCenters, rollCenters, corners)
    }

    // ---- Finding the sheet -------------------------------------------------------------------

    /** Picks the 4 candidates (and rotation) that best explain the printed layout. */
    private fun locateSheet(dark: FloatImage, all: List<MarkerCandidate>): Homography? {
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
        // All printed content lies inside the markers, so the real ones span the largest quadrilateral
        // among marks on the sheet; larger ones may come from clutter around it.
        quads.sortByDescending { it.first }

        var best: Homography? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for ((_, group) in quads.take(24)) {
            for (rot in 0 until 4) {
                val markers = List(4) { group[(it + rot) % 4] }
                val h = Homography.fromPoints(SheetLayout.markerCenters, markers.map { it.center }) ?: continue
                val s = Sampler(dark, h)
                val bar = s.barDarkness()
                if (bar < 0.45 || !markerSizesFit(h, markers) || s.marginDarkness() > 0.25) continue
                val ring = s.layoutScore()
                if (ring < 0.03) continue
                val score = bar + 2 * ring
                if (score > bestScore) { bestScore = score; best = h }
            }
        }
        return best
    }

    /** Each blob must be about as large as a printed marker would appear under [h]. */
    private fun markerSizesFit(h: Homography, markers: List<MarkerCandidate>): Boolean {
        val half = SheetLayout.MARKER_SIZE / 2
        return markers.indices.all { i ->
            val c = SheetLayout.markerCenters[i]
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
     * Paper is rarely perfectly flat, so after the corner-based mapping each block of bubbles, then each
     * row, is nudged to where the printed circles actually are.
     */
    private fun refineAnswers(s: Sampler, o: ReaderOptions): List<List<Pt>> {
        val nominal = List(o.questionCount) { q -> List(o.optionCount) { SheetLayout.answerBubble(q, it) } }
        val out = ArrayList<List<Pt>>()
        for (start in 0 until o.questionCount step SheetLayout.QUESTIONS_PER_COLUMN) {
            val rows = nominal.subList(start, min(o.questionCount, start + SheetLayout.QUESTIONS_PER_COLUMN))
            out.addAll(refineBlock(s, rows, SheetLayout.ANSWER_RADIUS))
        }
        return out
    }

    private fun refineRoll(s: Sampler): List<List<Pt>> {
        val rows = List(10) { d -> List(SheetLayout.ROLL_DIGITS) { SheetLayout.rollBubble(it, d) } }
        val refined = refineBlock(s, rows, SheetLayout.ROLL_RADIUS)
        return List(SheetLayout.ROLL_DIGITS) { pos -> List(10) { d -> refined[d][pos] } }
    }

    private fun refineBlock(s: Sampler, rows: List<List<Pt>>, r: Double): List<List<Pt>> {
        val sample = rows.flatten().let { all -> all.filterIndexed { i, _ -> i % max(1, all.size / 48) == 0 } }
        var block = bestOffset(s, sample, r, Pt(0.0, 0.0), 2.0, 0.5)
        block = bestOffset(s, sample, r, block, 0.5, 0.25)

        val offsets = rows.map { bestOffset(s, it, r, block, 1.0, 0.25) }
        val smooth = offsets.indices.map { i ->
            val win = offsets.subList(max(0, i - 2), min(offsets.size, i + 3))
            Pt(median(win.map { it.x }), median(win.map { it.y }))
        }
        return rows.mapIndexed { i, row -> row.map { Pt(it.x + smooth[i].x, it.y + smooth[i].y) } }
    }

    private fun bestOffset(s: Sampler, bubbles: List<Pt>, r: Double, around: Pt, range: Double, step: Double): Pt {
        var best = around
        var bestScore = Double.NEGATIVE_INFINITY
        val steps = (range / step).toInt()
        for (iy in -steps..steps) for (ix in -steps..steps) {
            val dx = around.x + ix * step
            val dy = around.y + iy * step
            var sum = 0.0
            for (b in bubbles) sum += s.ringContrast(b.x + dx, b.y + dy, r)
            // Small pull towards the starting point so blank areas don't drift.
            val score = sum / bubbles.size - 0.01 * (abs(ix) + abs(iy))
            if (score > bestScore) { bestScore = score; best = Pt(dx, dy) }
        }
        return best
    }

    // ---- Classification ----------------------------------------------------------------------

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

    /** How strongly a printed circle of radius [r] shows at (x, y): ring darkness minus the paper just outside. */
    fun ringContrast(x: Double, y: Double, r: Double): Double {
        var ring = 0.0
        var gap = 0.0
        for (k in 0 until RAYS) {
            val c = COS[k]
            val s = SIN[k]
            var best = 0f
            var t = r - 0.45
            while (t <= r + 0.451) {
                best = max(best, at(x + c * t, y + s * t))
                t += 0.225
            }
            ring += best
            gap += at(x + c * (r + 0.75), y + s * (r + 0.75))
        }
        return (ring - gap) / RAYS
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

    /** Average ring contrast over the roll-number grid, which is printed on every sheet. */
    fun layoutScore(): Double {
        var sum = 0.0
        var n = 0
        for (pos in 0 until SheetLayout.ROLL_DIGITS) for (d in 0..9) {
            val c = SheetLayout.rollBubble(pos, d)
            sum += ringContrast(c.x, c.y, SheetLayout.ROLL_RADIUS)
            n++
        }
        return sum / n
    }

    /** Darkness of the blank paper around the corner markers (should be near 0). */
    fun marginDarkness(): Double {
        var sum = 0.0
        var n = 0
        for (c in SheetLayout.markerCenters) {
            for ((dx, dy) in MARGIN_OFFSETS) { sum += at(c.x + dx, c.y + dy); n++ }
        }
        return sum / n
    }

    fun barDarkness(): Double {
        val y = (SheetLayout.BAR_TOP + SheetLayout.BAR_BOTTOM) / 2
        var sum = 0.0
        val xs = 5
        for (i in 0 until xs) sum += at(SheetLayout.BAR_LEFT + 2.0 + i * (SheetLayout.BAR_RIGHT - SheetLayout.BAR_LEFT - 4.0) / (xs - 1), y)
        return sum / xs
    }

    private companion object {
        const val RAYS = 16
        val COS = DoubleArray(RAYS) { cos(2 * PI * it / RAYS) }
        val SIN = DoubleArray(RAYS) { sin(2 * PI * it / RAYS) }
        val MARGIN_OFFSETS = listOf(7.0 to 0.0, -7.0 to 0.0, 0.0 to 7.0, 0.0 to -7.0)
    }
}
