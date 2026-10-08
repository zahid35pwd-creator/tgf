package com.omrscanner.core

import kotlin.math.max
import kotlin.math.min

/** A solid, square-looking dark blob that could be one of the sheet's corner markers. */
internal class MarkerCandidate(val center: Pt, val area: Int)

internal object MarkerFinder {
    private const val DARK = 0.4f

    fun find(dark: FloatImage): List<MarkerCandidate> {
        val w = dark.width
        val h = dark.height
        val labels = IntArray(w * h)
        val parent = IntArrayList()
        parent.add(0) // label 0 = background

        fun findRoot(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }

        fun union(a: Int, b: Int): Int {
            val ra = findRoot(a)
            val rb = findRoot(b)
            if (ra == rb) return ra
            val lo = min(ra, rb)
            parent[max(ra, rb)] = lo
            return lo
        }

        // First pass: provisional labels with 8-connectivity.
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (dark.data[row + x] < DARK) continue
                var label = 0
                if (x > 0) label = merge(label, labels[row + x - 1], ::union)
                if (y > 0) {
                    val up = row - w
                    if (x > 0) label = merge(label, labels[up + x - 1], ::union)
                    label = merge(label, labels[up + x], ::union)
                    if (x < w - 1) label = merge(label, labels[up + x + 1], ::union)
                }
                if (label == 0) { label = parent.size; parent.add(label) }
                labels[row + x] = label
            }
        }

        // Second pass: resolve labels and gather statistics per component.
        val n = parent.size
        val area = IntArray(n)
        val sumX = DoubleArray(n)
        val sumY = DoubleArray(n)
        val minX = IntArray(n) { Int.MAX_VALUE }
        val minY = IntArray(n) { Int.MAX_VALUE }
        val maxX = IntArray(n) { -1 }
        val maxY = IntArray(n) { -1 }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (labels[i] == 0) continue
            val r = findRoot(labels[i])
            labels[i] = r
            area[r]++
            sumX[r] += x.toDouble()
            sumY[r] += y.toDouble()
            if (x < minX[r]) minX[r] = x
            if (x > maxX[r]) maxX[r] = x
            if (y < minY[r]) minY[r] = y
            if (y > maxY[r]) maxY[r] = y
        }

        val imageArea = w.toDouble() * h
        val minArea = max(40.0, imageArea * 0.00003)
        val maxArea = imageArea * 0.01
        val out = ArrayList<MarkerCandidate>()
        for (r in 1 until n) {
            val a = area[r]
            if (a < minArea || a > maxArea) continue
            val bw = maxX[r] - minX[r] + 1
            val bh = maxY[r] - minY[r] + 1
            if (bw < 5 || bh < 5) continue
            if (max(bw, bh).toDouble() / min(bw, bh) > 2.5) continue
            if (a.toDouble() / (bw * bh) < 0.4) continue
            if (!isSolidQuad(labels, w, r, minX[r], maxX[r], minY[r], maxY[r], a)) continue
            out.add(MarkerCandidate(Pt(sumX[r] / a, sumY[r] / a), a))
        }
        return out
    }

    private inline fun merge(current: Int, neighbour: Int, union: (Int, Int) -> Int): Int = when {
        neighbour == 0 -> current
        current == 0 -> neighbour
        current == neighbour -> current
        else -> union(current, neighbour)
    }

    /** Solid (no holes or concavities) and four-cornered rather than round, so filled bubbles are rejected. */
    private fun isSolidQuad(labels: IntArray, w: Int, r: Int, x0: Int, x1: Int, y0: Int, y1: Int, area: Int): Boolean {
        val pts = ArrayList<Pt>()
        for (y in y0..y1) {
            var left = -1
            var right = -1
            for (x in x0..x1) if (labels[y * w + x] == r) { if (left < 0) left = x; right = x }
            if (left < 0) continue
            pts.add(Pt(left - 0.5, y - 0.5)); pts.add(Pt(left - 0.5, y + 0.5))
            pts.add(Pt(right + 0.5, y - 0.5)); pts.add(Pt(right + 0.5, y + 0.5))
        }
        val hull = Polygons.convexHull(pts)
        val hullArea = Polygons.area(hull)
        if (hullArea <= 0 || area / hullArea < 0.85) return false
        // A square fills its inscribed quad (ratio ~1); a disc only 2/pi (0.64). Blur rounds the
        // corners of small markers, so allow a fair margin above the disc.
        val quad = Polygons.quadOnHull(hull) ?: return false
        return Polygons.area(quad) / hullArea > 0.72
    }
}

/** Growable int array, to avoid boxing in the labelling pass. */
internal class IntArrayList {
    private var data = IntArray(1024)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
    operator fun set(i: Int, v: Int) { data[i] = v }
}
