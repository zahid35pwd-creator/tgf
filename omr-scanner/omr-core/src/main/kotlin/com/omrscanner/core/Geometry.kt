package com.omrscanner.core

import kotlin.math.abs

/** Projective transform stored row-major in [m] (9 values, m[8] normalised to 1). */
class Homography(val m: DoubleArray) {

    fun map(p: Pt): Pt {
        val d = m[6] * p.x + m[7] * p.y + m[8]
        return Pt((m[0] * p.x + m[1] * p.y + m[2]) / d, (m[3] * p.x + m[4] * p.y + m[5]) / d)
    }

    companion object {
        /** Solves for the homography taking each of 4 [src] points to the matching [dst] point. */
        fun fromPoints(src: List<Pt>, dst: List<Pt>): Homography? {
            require(src.size == 4 && dst.size == 4)
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val h = solve(a) ?: return null
            return Homography(DoubleArray(9) { if (it < 8) h[it] else 1.0 })
        }

        /** Gaussian elimination with partial pivoting on an n x (n+1) augmented matrix. */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                if (abs(a[pivot][col]) < 1e-12) return null
                val t = a[col]; a[col] = a[pivot]; a[pivot] = t
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col] / a[col][col]
                    if (f == 0.0) continue
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}

internal object Polygons {
    fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    /** Andrew's monotone chain; returns the hull counter-clockwise without repeating the first point. */
    fun convexHull(points: List<Pt>): List<Pt> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        val hull = ArrayList<Pt>(pts.size * 2)
        for (p in pts) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull.add(p)
        }
        val lowerSize = hull.size + 1
        for (i in pts.size - 2 downTo 0) {
            val p = pts[i]
            while (hull.size >= lowerSize && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull.add(p)
        }
        hull.removeAt(hull.size - 1)
        return hull
    }

    fun area(poly: List<Pt>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    /** The largest-area quadrilateral (approximately) whose corners lie on [hull]. */
    fun quadOnHull(hull: List<Pt>): List<Pt>? {
        if (hull.size < 4) return null
        var bestD = -1.0
        var ia = 0
        var ic = 0
        for (i in hull.indices) for (j in i + 1 until hull.size) {
            val dx = hull[i].x - hull[j].x
            val dy = hull[i].y - hull[j].y
            val d = dx * dx + dy * dy
            if (d > bestD) { bestD = d; ia = i; ic = j }
        }
        val a = hull[ia]
        val c = hull[ic]
        var b: Pt? = null
        var d: Pt? = null
        var bestB = 0.0
        var bestDist = 0.0
        for (p in hull) {
            val s = cross(a, c, p)
            if (s > bestB) { bestB = s; b = p }
            if (-s > bestDist) { bestDist = -s; d = p }
        }
        if (b == null || d == null) return null
        return listOf(a, b, c, d)
    }
}
