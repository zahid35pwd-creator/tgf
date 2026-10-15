package com.omrscanner.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a group of detected bubbles is used for. */
enum class GroupKind { ANSWERS, NUMBER, LETTERS, IGNORE }

/**
 * Bubbles that sit together on the blank sheet, e.g. one answer column or a roll-number grid.
 * [rows] are top to bottom, each sorted left to right; [columns] are left to right, each sorted top to bottom.
 */
class BubbleGroup(
    val rows: List<List<Pt>>,
    val columns: List<List<Pt>>,
    val radius: Double,
    var kind: GroupKind,
    var name: String,
) {
    val count get() = rows.sumOf { it.size }
    val left get() = columns.first().first().x
    val top get() = rows.first().first().y
    val right get() = columns.last().first().x
    val bottom get() = rows.last().first().y

    fun describe(): String = when (kind) {
        GroupKind.ANSWERS -> "${rows.size} questions × ${rows.maxOf { it.size }} options"
        GroupKind.NUMBER -> "Number field, ${columns.size} digit${if (columns.size == 1) "" else "s"}"
        GroupKind.LETTERS -> "Letter code, ${columns.maxOf { it.size }} choices"
        GroupKind.IGNORE -> "Ignored (${count} circles)"
    }
}

/** The result of studying a blank sheet; the user reviews [groups] and then calls [toTemplate]. */
class LearnedSheet(
    val pageWidth: Double,
    val pageHeight: Double,
    val alignment: Alignment,
    val markerCenters: List<Pt>,
    val markerSize: Double,
    val inkMode: InkMode,
    val orientationMarks: List<Pt>,
    val groups: List<BubbleGroup>,
    /** The blank sheet, flattened, at [pxPerMm]. */
    val page: ArgbImage,
    val pxPerMm: Double,
) {
    /**
     * Builds the reader's template. Questions are numbered block by block (blocks left to right in bands
     * from the top), or straight across all blocks of a band when [acrossRows] is set.
     */
    fun toTemplate(
        id: String,
        name: String,
        idField: String?,
        setField: String?,
        optionLabels: List<String>,
        acrossRows: Boolean = false,
    ): SheetTemplate {
        val answerGroups = groups.filter { it.kind == GroupKind.ANSWERS }
        require(answerGroups.isNotEmpty()) { "No answer area selected" }
        val questions = ArrayList<List<Pt>>()
        val blocks = ArrayList<List<Int>>()
        for (band in bands(answerGroups)) {
            if (acrossRows) {
                val blockLists = band.map { ArrayList<Int>() }
                val maxRows = band.maxOf { it.rows.size }
                for (r in 0 until maxRows) band.forEachIndexed { b, g ->
                    g.rows.getOrNull(r)?.let { blockLists[b].add(questions.size); questions.add(it) }
                }
                blocks.addAll(blockLists)
            } else {
                for (g in band) {
                    blocks.add(g.rows.indices.map { questions.size + it })
                    questions.addAll(g.rows)
                }
            }
        }
        val fields = groups.filter { it.kind == GroupKind.NUMBER || it.kind == GroupKind.LETTERS }.map { g ->
            val labels = if (g.kind == GroupKind.NUMBER) SheetTemplate.DIGITS else optionLabels
            DigitField(g.name, g.columns.map { it.take(labels.size) }, g.radius, labels.take(g.columns.maxOf { it.size }))
        }
        return SheetTemplate(
            id = id,
            name = name,
            markerCenters = if (alignment == Alignment.MARKERS) markerCenters else listOf(Pt(0.0, 0.0), Pt(pageWidth, 0.0), Pt(pageWidth, pageHeight), Pt(0.0, pageHeight)),
            markerSize = markerSize,
            questions = questions,
            answerRadius = answerGroups.map { it.radius }.sorted()[answerGroups.size / 2],
            answerBlocks = blocks,
            fields = fields,
            idField = idField?.takeIf { n -> fields.any { it.name == n } },
            setField = setField?.takeIf { n -> fields.any { it.name == n } },
            orientationMarks = orientationMarks,
            inkMode = inkMode,
            printable = false,
            optionLabels = optionLabels,
            alignment = alignment,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            custom = true,
        )
    }

    /** Groups side by side (overlapping vertically) form a band; bands go top to bottom. */
    private fun bands(gs: List<BubbleGroup>): List<List<BubbleGroup>> {
        val out = ArrayList<MutableList<BubbleGroup>>()
        for (g in gs.sortedBy { it.top }) {
            val band = out.firstOrNull { b -> b.any { o -> g.top < o.bottom && o.top < g.bottom } }
            if (band != null) band.add(g) else out.add(mutableListOf(g))
        }
        return out.map { b -> b.sortedBy { it.left } }
    }
}

/** Learns a sheet's layout from a picture or PDF page of a blank copy. */
object TemplateLearner {
    private const val PX_PER_MM = 8.0

    fun learn(image: ArgbImage): LearnedSheet {
        val work = ImageOps.downscale(image, 2600)
        val paper = ImageOps.paperQuad(ImageOps.luminance(work))
            ?: throw OmrException("Could not find the edges of the sheet. Use a flat scan or PDF of the blank sheet, or photograph it on a darker background.")
        // Page size: A4 / Letter / Legal when the proportions are close, otherwise as measured.
        val top = dist(paper[0], paper[1]); val bottom = dist(paper[3], paper[2])
        val leftSide = dist(paper[0], paper[3]); val rightSide = dist(paper[1], paper[2])
        var corners = paper
        var aspect = (leftSide + rightSide) / (top + bottom)
        if (aspect < 1) { corners = List(4) { paper[(it + 1) % 4] }; aspect = 1 / aspect } // keep portrait
        val (w, h) = listOf(210.0 to 297.0, 215.9 to 279.4, 215.9 to 355.6)
            .firstOrNull { (pw, ph) -> abs(ph / pw - aspect) / aspect < 0.04 } ?: (210.0 to 210.0 * aspect)
        val pageToImage = Homography.fromPoints(listOf(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, h), Pt(0.0, h)), corners)
            ?: throw OmrException("Could not flatten the sheet.")
        val page = ImageOps.rectify(work, pageToImage, PX_PER_MM, w, h)
        return analyse(page, w, h)
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    private fun analyse(page: ArgbImage, w: Double, h: Double): LearnedSheet {
        val s = PX_PER_MM
        val lum = ImageOps.luminance(page)
        val dark = ImageOps.darkness(lum)

        // Corner squares: solid black, similar size, spanning most of the page.
        val black = ImageOps.blackOnly(page)
        val squares = MarkerFinder.find(black).filter { sqrt(it.area.toDouble()) / s in 3.0..14.0 }
        val markers = bestCornerSquares(squares, w * h * s * s)
        val alignment = if (markers != null) Alignment.MARKERS else Alignment.PAGE_EDGES
        val markerCenters = markers?.map { Pt(it.center.x / s, it.center.y / s) } ?: emptyList()
        val markerSize = markers?.let { m -> m.map { sqrt(it.area.toDouble()) / s }.average() } ?: 0.0

        // The print colour decides how fills are read; circles are then looked for in print-coloured
        // pixels only, so black digits printed inside coloured circles don't spoil their shape.
        val inkMode = printColour(page, dark)
        val printOnly = if (inkMode == InkMode.LUMINANCE) dark else {
            val inkDark = ImageOps.ink(page, inkMode, dark)
            FloatImage(dark.width, dark.height, FloatArray(dark.data.size) { if (inkDark.data[it] < 0.35f) dark.data[it] else 0f })
        }
        val rings = findRings(printOnly)
        val groups = group(rings, printOnly)

        // Orientation: black blobs whose spot, turned half round, is blank paper.
        val marks = blackSpots(black, s).filter { p ->
            val q = Pt(w - p.x, h - p.y)
            black.sample(q.x * s, q.y * s) < 0.2 && markerCenters.none { m -> dist(m, p) < 6.0 }
        }.take(8)

        return LearnedSheet(w, h, alignment, markerCenters, markerSize, inkMode, marks, groups, page, s)
    }

    private fun bestCornerSquares(c: List<MarkerCandidate>, pageArea: Double): List<MarkerCandidate>? {
        val cands = c.sortedByDescending { it.area }.take(30)
        var best: List<MarkerCandidate>? = null
        var bestArea = 0.3 * pageArea
        for (a in cands.indices) for (b in a + 1 until cands.size) for (d in b + 1 until cands.size) for (e in d + 1 until cands.size) {
            val g = listOf(cands[a], cands[b], cands[d], cands[e])
            if (g.maxOf { it.area } > 2.0 * g.minOf { it.area }) continue
            val cx = g.sumOf { it.center.x } / 4; val cy = g.sumOf { it.center.y } / 4
            val o = g.sortedBy { kotlin.math.atan2(it.center.y - cy, it.center.x - cx) }
            if ((0 until 4).any { Polygons.cross(o[it].center, o[(it + 1) % 4].center, o[(it + 2) % 4].center) <= 0 }) continue
            val area = Polygons.area(o.map { it.center })
            if (area > bestArea) { bestArea = area; best = o }
        }
        // Start from the one nearest the page's top-left so the order is top-left, top-right, …
        return best?.let { o -> val i = o.indices.minBy { o[it].center.x + o[it].center.y }; List(4) { o[(i + it) % 4] } }
    }

    /** Ring centres and radii (mm) from connected dark components that are hollow and round. */
    private fun findRings(dark: FloatImage): List<Ring> {
        val s = PX_PER_MM
        val comps = Components.of(dark, 0.3f)
        val out = ArrayList<Ring>()
        for (c in comps) {
            val bw = (c.maxX - c.minX + 1) / s
            val bh = (c.maxY - c.minY + 1) / s
            if (bw !in 2.4..7.0 || bh !in 2.4..7.0 || max(bw, bh) / min(bw, bh) > 1.3) continue
            if (c.area.toDouble() / ((c.maxX - c.minX + 1) * (c.maxY - c.minY + 1)) > 0.5) continue // solid, not a ring
            // The middle must be open paper (or a small letter), not a thick blob.
            val cx = (c.minX + c.maxX) / 2.0
            val cy = (c.minY + c.maxY) / 2.0
            val rPx = (bw + bh) / 4 * s
            val d = c.pixels.map { p -> hypot(p % dark.width - cx, (p / dark.width) - cy) }
            if (d.count { it > 0.6 * rPx } < 0.6 * c.area) continue
            // Round, not a square box: no ink out in the corners.
            if (d.count { it > 1.12 * rPx } > 0.05 * c.area) continue
            out.add(Ring(Pt(cx / s, cy / s), (bw + bh) / 4))
        }
        return out
    }

    class Ring(val center: Pt, val radius: Double)

    /**
     * Coloured print vanishes in the channel where it is brightest; black print needs plain brightness.
     * Decided from the median colour of all clearly printed pixels.
     */
    private fun printColour(page: ArgbImage, dark: FloatImage): InkMode {
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        var i = 0
        while (i < page.pixels.size) {
            if (dark.data[i] > 0.3f) {
                val v = page.pixels[i]
                rs.add((v shr 16) and 0xFF); gs.add((v shr 8) and 0xFF); bs.add(v and 0xFF)
            }
            i += 3
        }
        if (rs.isEmpty()) return InkMode.LUMINANCE
        val r = rs.sorted()[rs.size / 2]; val g = gs.sorted()[gs.size / 2]; val b = bs.sorted()[bs.size / 2]
        val hi = maxOf(r, g, b)
        if (hi - minOf(r, g, b) < 60) return InkMode.LUMINANCE
        return when (hi) { r -> InkMode.RED; g -> InkMode.GREEN; else -> InkMode.BLUE }
    }

    /** Clusters rings into grids: same size, sharing rows or columns at bubble-like spacing. */
    private fun group(rings: List<Ring>, print: FloatImage): List<BubbleGroup> {
        val n = rings.size
        val parent = IntArray(n) { it }
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; return r }
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = rings[i]; val b = rings[j]
            val r = (a.radius + b.radius) / 2
            if (max(a.radius, b.radius) / min(a.radius, b.radius) > 1.25) continue
            val dx = abs(a.center.x - b.center.x); val dy = abs(a.center.y - b.center.y)
            val sameRow = dy < 0.35 * r && dx < 5.5 * r
            val sameCol = dx < 0.35 * r && dy < 6.0 * r
            if (sameRow || sameCol) parent[root(i)] = root(j)
        }
        val byRoot = (0 until n).groupBy { root(it) }.values.map { idx -> idx.map { rings[it] } }
        val groups = ArrayList<BubbleGroup>()
        for (found in byRoot) {
            if (found.size < 4) continue
            val r = found.map { it.radius }.sorted()[found.size / 2]
            val members = complete(found, r, print)
            // Snap every circle to its row's and column's average line: sheets are printed on a regular grid,
            // and this removes the error of any single circle whose outline touches other print.
            val rowLines = cluster(members) { it.center.y }.map { c -> c.map { it.center.y }.average() }
            val colLines = cluster(members) { it.center.x }.map { c -> c.map { it.center.x }.average() }
            val snapped = members.map { m -> Pt(colLines.minBy { abs(it - m.center.x) }, rowLines.minBy { abs(it - m.center.y) }) }.distinct()
            val rows = rowLines.map { y -> snapped.filter { it.y == y }.sortedBy { it.x } }.filter { it.isNotEmpty() }
            val cols = colLines.map { x -> snapped.filter { it.x == x }.sortedBy { it.y } }.filter { it.isNotEmpty() }
            if (rows.size < 2 && cols.size < 2) continue
            groups.add(BubbleGroup(rows, cols, r, classify(rows, cols), ""))
        }
        // Default names: the first number field (reading order) with 5+ digits is the roll / ID, else the widest;
        // a short letter column is the set.
        val ordered = groups.sortedWith(compareBy({ (it.top / 20).roundToInt() }, { it.left }))
        val numbers = ordered.filter { it.kind == GroupKind.NUMBER }
        val roll = numbers.firstOrNull { it.columns.size >= 5 } ?: numbers.maxByOrNull { it.columns.size }
        var codes = 0
        for (g in ordered) when {
            g === roll -> g.name = "Roll"
            g.kind == GroupKind.NUMBER -> g.name = "Code ${++codes}"
            g.kind == GroupKind.LETTERS -> g.name = "Set"
        }
        return ordered
    }

    /**
     * Circles touching printed lines can be missed; fill in grid positions (and one step past each end of
     * the columns) wherever a printed circle is actually visible.
     */
    private fun complete(found: List<Ring>, r: Double, print: FloatImage): List<Ring> {
        var out = found
        repeat(2) {
            // Grid lines, ignoring lines backed by very few circles (stray or badly centred detections).
            fun lines(key: (Ring) -> Double): List<Double> {
                val cs = cluster(out, key)
                val most = cs.maxOf { it.size }
                return cs.filter { cs.size <= 2 || it.size >= max(1.0, 0.2 * most) }.map { c -> c.map(key).average() }
            }
            val xs = lines { it.center.x }
            val ys = lines { it.center.y }
            // Row spacing from the whole span, so small errors in single gaps don't add up.
            val pitch = ys.zipWithNext { a, b -> b - a }.filter { it > 2 * r }.minOrNull()
                ?.let { step -> (ys.last() - ys.first()) / Math.round((ys.last() - ys.first()) / step).coerceAtLeast(1) }
            // Evenly spaced rows (gaps included) when the found rows fit a regular grid; else just the ends.
            val regular = pitch != null && ys.all { y -> val k = (y - ys.first()) / pitch; abs(k - Math.round(k)) * pitch < 0.3 * r }
            val rowsToTry = when {
                pitch == null -> ys
                regular -> (-1..(Math.round((ys.last() - ys.first()) / pitch).toInt() + 1)).map { ys.first() + it * pitch }
                else -> listOf(ys.first() - pitch) + ys + (ys.last() + pitch)
            }
            val prev = out
            out = xs.flatMap { x ->
                rowsToTry.mapNotNull { y ->
                    val seen = prev.any { abs(it.center.x - x) < 0.5 * r && abs(it.center.y - y) < 0.5 * r }
                    if (seen || looksLikeCircle(print, Pt(x, y), r)) Ring(Pt(x, y), r) else null
                }
            }
            if (out.isEmpty()) return found
        }
        return out
    }

    /** A dark outline all the way round at radius [r] around [c] (mm). */
    private fun looksLikeCircle(print: FloatImage, c: Pt, r: Double): Boolean {
        val s = PX_PER_MM
        var ring = 0.0
        var hits = 0
        for (k in 0 until 24) {
            val a = 2 * Math.PI * k / 24
            var best = 0f
            var t = r - 0.4
            while (t <= r + 0.4) { best = max(best, print.sample((c.x + t * kotlin.math.cos(a)) * s, (c.y + t * kotlin.math.sin(a)) * s)); t += 0.1 }
            ring += best
            if (best > 0.3f) hits++
        }
        // Only the outline is checked: letters are often printed inside the circles.
        return hits >= 18 && ring / 24 > 0.35
    }

    private fun classify(rows: List<List<Pt>>, cols: List<List<Pt>>): GroupKind {
        val tallest = cols.maxOf { it.size }
        val widest = rows.maxOf { it.size }
        return when {
            tallest == 10 && widest !in 4..5 -> GroupKind.NUMBER
            cols.size == 1 && tallest in 2..5 -> GroupKind.LETTERS
            widest in 2..6 && rows.size >= 2 -> GroupKind.ANSWERS
            tallest == 10 -> GroupKind.NUMBER
            else -> GroupKind.IGNORE
        }
    }

    private fun cluster(rings: List<Ring>, key: (Ring) -> Double): List<List<Ring>> {
        val sorted = rings.sortedBy(key)
        val tol = 0.45 * sorted.map { it.radius }.average()
        val out = ArrayList<MutableList<Ring>>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && key(r) - last.map(key).average() < tol) last.add(r) else out.add(mutableListOf(r))
        }
        return out
    }

    /** Centres (mm) of small solid black blobs: timing marks, logos, QR modules. */
    private fun blackSpots(black: FloatImage, s: Double): List<Pt> =
        Components.of(black, 0.5f)
            .filter { c -> c.area / (s * s) in 1.5..60.0 && c.area.toDouble() / ((c.maxX - c.minX + 1) * (c.maxY - c.minY + 1)) > 0.8 }
            .map { c -> Pt((c.minX + c.maxX) / 2.0 / s, (c.minY + c.maxY) / 2.0 / s) }
}

/** Connected components of pixels at or above a darkness level (4-connected), with their pixel lists. */
internal class Component(val area: Int, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val pixels: IntArray)

internal object Components {
    fun of(img: FloatImage, level: Float, maxArea: Int = 20000): List<Component> {
        val w = img.width
        val seen = BooleanArray(img.data.size)
        val out = ArrayList<Component>()
        val stack = IntArrayList()
        val buf = IntArrayList()
        for (start in img.data.indices) {
            if (seen[start] || img.data[start] < level) continue
            seen[start] = true
            stack.add(start)
            buf.clear()
            var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
            while (stack.size > 0) {
                val p = stack.pop()
                buf.add(p)
                val x = p % w; val y = p / w
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                for (q in intArrayOf(p - 1, p + 1, p - w, p + w)) {
                    if (q < 0 || q >= img.data.size || seen[q] || img.data[q] < level) continue
                    if ((q == p - 1 && x == 0) || (q == p + 1 && x == w - 1)) continue
                    seen[q] = true
                    stack.add(q)
                }
            }
            if (buf.size <= maxArea) out.add(Component(buf.size, minX, maxX, minY, maxY, buf.toArray()))
        }
        return out
    }
}
