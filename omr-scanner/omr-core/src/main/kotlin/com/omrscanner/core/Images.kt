package com.omrscanner.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An image as packed 0xAARRGGBB pixels, row by row (the layout of android.graphics.Bitmap.getPixels). */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixel array does not match ${width}x$height" }
    }
}

/** Single-channel float image; pixel centres sit on integer coordinates. */
internal class FloatImage(val width: Int, val height: Int, val data: FloatArray = FloatArray(width * height)) {
    operator fun get(x: Int, y: Int) = data[y * width + x]

    /** Bilinear sample; anything outside the image reads as 0. */
    fun sample(x: Double, y: Double): Float {
        if (x < 0.0 || y < 0.0 || x > width - 1.0 || y > height - 1.0) return 0f
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = (x - x0).toFloat()
        val fy = (y - y0).toFloat()
        val top = data[y0 * width + x0] * (1 - fx) + data[y0 * width + x1] * fx
        val bottom = data[y1 * width + x0] * (1 - fx) + data[y1 * width + x1] * fx
        return top * (1 - fy) + bottom * fy
    }
}

internal object ImageOps {

    /** Area-averaging downscale so the longer side is at most [maxSide]. */
    fun downscale(src: ArgbImage, maxSide: Int): ArgbImage {
        val longSide = max(src.width, src.height)
        if (longSide <= maxSide) return src
        val scale = longSide.toDouble() / maxSide
        val w = max(1, (src.width / scale).roundToInt())
        val h = max(1, (src.height / scale).roundToInt())
        val out = IntArray(w * h)
        for (dy in 0 until h) {
            val sy0 = floor(dy * scale).toInt()
            val sy1 = min(src.height, max(sy0 + 1, floor((dy + 1) * scale).toInt()))
            for (dx in 0 until w) {
                val sx0 = floor(dx * scale).toInt()
                val sx1 = min(src.width, max(sx0 + 1, floor((dx + 1) * scale).toInt()))
                var r = 0
                var g = 0
                var b = 0
                for (sy in sy0 until sy1) {
                    val row = sy * src.width
                    for (sx in sx0 until sx1) {
                        val p = src.pixels[row + sx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                val n = (sy1 - sy0) * (sx1 - sx0)
                out[dy * w + dx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return ArgbImage(w, h, out)
    }

    fun luminance(src: ArgbImage): FloatImage {
        val out = FloatImage(src.width, src.height)
        for (i in src.pixels.indices) {
            val p = src.pixels[i]
            out.data[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }
        return out
    }

    /** One colour channel (0 = red, 1 = green, 2 = blue): print in that colour looks almost white. */
    fun channel(src: ArgbImage, index: Int): FloatImage {
        val shift = 16 - 8 * index
        val out = FloatImage(src.width, src.height)
        for (i in src.pixels.indices) out.data[i] = ((src.pixels[i] shr shift) and 0xFF).toFloat()
        return out
    }

    /** The image the reader judges fills on, for a sheet printed in the colour [mode] ignores. */
    fun ink(src: ArgbImage, mode: InkMode, luminanceDarkness: FloatImage): FloatImage = when (mode) {
        InkMode.LUMINANCE -> luminanceDarkness
        InkMode.RED -> darkness(channel(src, 0))
        InkMode.GREEN -> darkness(channel(src, 1))
        InkMode.BLUE -> darkness(channel(src, 2))
    }

    /** Darkness of the lightest channel: only black (or very dark) marks show up. */
    fun blackOnly(src: ArgbImage): FloatImage {
        val out = FloatImage(src.width, src.height)
        for (i in src.pixels.indices) {
            val p = src.pixels[i]
            out.data[i] = max(max((p shr 16) and 0xFF, (p shr 8) and 0xFF), p and 0xFF).toFloat()
        }
        return darkness(out)
    }

    /**
     * The four corners of the sheet of paper (clockwise on screen, from any corner), for sheets without
     * corner squares. The paper must be brighter than what is around it; if it fills the picture (a flat
     * scan or a PDF page), the picture's own corners are returned.
     */
    fun paperQuad(gray: FloatImage): List<Pt>? {
        // Work small: the outline only needs to be roughly right, alignment is refined later.
        val f = max(1, max(gray.width, gray.height) / 500)
        val w = gray.width / f
        val h = gray.height / f
        val small = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (dy in 0 until f) for (dx in 0 until f) s += gray[x * f + dx, y * f + dy]
            small[y * w + x] = s / (f * f)
        }
        // Otsu split between paper and background.
        val hist = IntArray(256)
        for (v in small) hist[v.toInt().coerceIn(0, 255)]++
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var wB = 0.0; var sumB = 0.0; var best = -1.0; var cut = 128
        for (i in 0 until 256) {
            wB += hist[i]; if (wB == 0.0) continue
            val wF = small.size - wB; if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; cut = i }
        }
        val bright = BooleanArray(small.size) { small[it] > cut }
        val fullFrame = listOf(Pt(0.0, 0.0), Pt(gray.width - 1.0, 0.0), Pt(gray.width - 1.0, gray.height - 1.0), Pt(0.0, gray.height - 1.0))
        // A uniform picture, or paper reaching every edge: the page fills the frame.
        if (best < 1e-6 || bright.count { it } > 0.97 * small.size) return fullFrame
        // Largest bright region.
        val label = IntArray(small.size) { -1 }
        var bestLabel = -1; var bestSize = 0
        val stack = IntArrayList()
        var next = 0
        for (i in small.indices) {
            if (!bright[i] || label[i] >= 0) continue
            var size = 0
            stack.add(i); label[i] = next
            while (stack.size > 0) {
                val p = stack.pop(); size++
                val x = p % w; val y = p / w
                if (x > 0 && bright[p - 1] && label[p - 1] < 0) { label[p - 1] = next; stack.add(p - 1) }
                if (x < w - 1 && bright[p + 1] && label[p + 1] < 0) { label[p + 1] = next; stack.add(p + 1) }
                if (y > 0 && bright[p - w] && label[p - w] < 0) { label[p - w] = next; stack.add(p - w) }
                if (y < h - 1 && bright[p + w] && label[p + w] < 0) { label[p + w] = next; stack.add(p + w) }
            }
            if (size > bestSize) { bestSize = size; bestLabel = next }
            next++
        }
        if (bestSize < 0.12 * small.size) return null
        if (bestSize > 0.85 * small.size && touchesAllEdges(BooleanArray(small.size) { label[it] == bestLabel }, w, h)) return fullFrame
        val pts = ArrayList<Pt>()
        for (y in 0 until h) {
            var l = -1; var r = -1
            for (x in 0 until w) if (label[y * w + x] == bestLabel) { if (l < 0) l = x; r = x }
            if (l >= 0) { pts.add(Pt(l * f.toDouble(), (y + 0.5) * f)); pts.add(Pt((r + 1) * f.toDouble(), (y + 0.5) * f)) }
        }
        val hull = Polygons.convexHull(pts)
        val quad = Polygons.quadOnHull(hull) ?: return null
        if (Polygons.area(quad) < 0.7 * Polygons.area(hull)) return null
        val cx = quad.sumOf { it.x } / 4
        val cy = quad.sumOf { it.y } / 4
        return quad.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
    }

    private fun touchesAllEdges(b: BooleanArray, w: Int, h: Int): Boolean {
        fun any(range: IntProgression) = range.any { b[it] }
        return any(0 until w) && any((h - 1) * w until h * w) && any(0 until h * w step w) && any(w - 1 until h * w step w)
    }

    /**
     * Converts luminance to "darkness" in 0..1 relative to the local paper brightness, which removes
     * shadows and uneven lighting. Paper brightness is estimated with a coarse max filter that is wider
     * than any solid mark on the sheet, so even the corner markers stay fully dark.
     */
    fun darkness(gray: FloatImage): FloatImage {
        val cell = 8
        val sw = ceil(gray.width / cell.toDouble()).toInt()
        val sh = ceil(gray.height / cell.toDouble()).toInt()
        var bg = FloatArray(sw * sh)
        for (cy in 0 until sh) for (cx in 0 until sw) {
            // Max over 2x2 averages: robust to single-pixel noise.
            var best = 0f
            val yEnd = min(gray.height - 1, (cy + 1) * cell)
            val xEnd = min(gray.width - 1, (cx + 1) * cell)
            var y = cy * cell
            while (y < yEnd) {
                var x = cx * cell
                while (x < xEnd) {
                    val v = (gray[x, y] + gray[x + 1, y] + gray[x, y + 1] + gray[x + 1, y + 1]) * 0.25f
                    if (v > best) best = v
                    x += 2
                }
                y += 2
            }
            if (yEnd <= cy * cell || xEnd <= cx * cell) best = gray[min(cx * cell, gray.width - 1), min(cy * cell, gray.height - 1)]
            bg[cy * sw + cx] = best
        }
        // A marker spans about 1/26 of the sheet width; cover more than half of it.
        val radius = ceil(min(gray.width, gray.height) / (40.0 * cell)).toInt() + 2
        bg = maxFilter(bg, sw, sh, radius)
        bg = boxBlur(bg, sw, sh, 2)

        val small = FloatImage(sw, sh, bg)
        val out = FloatImage(gray.width, gray.height)
        for (y in 0 until gray.height) {
            val sy = ((y + 0.5) / cell - 0.5).coerceIn(0.0, sh - 1.0)
            for (x in 0 until gray.width) {
                val sx = ((x + 0.5) / cell - 0.5).coerceIn(0.0, sw - 1.0)
                val b = small.sample(sx, sy)
                val i = y * gray.width + x
                out.data[i] = if (b < 24f) 0f else (1f - gray.data[i] / b).coerceIn(0f, 1f)
            }
        }
        return out
    }

    private fun maxFilter(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, src[y * w + k])
            tmp[y * w + x] = m
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp[k * w + x])
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) { s += src[y * w + k]; n++ }
            tmp[y * w + x] = s / n
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) { s += tmp[k * w + x]; n++ }
            out[y * w + x] = s / n
        }
        return out
    }

    /** Resamples the sheet area of [src] into an upright, flat image at [pxPerMm]. */
    fun rectify(
        src: ArgbImage, sheetToImage: Homography, pxPerMm: Double,
        pageWidth: Double = SheetLayout.PAGE_WIDTH, pageHeight: Double = SheetLayout.PAGE_HEIGHT,
    ): ArgbImage {
        val w = (pageWidth * pxPerMm).roundToInt()
        val h = (pageHeight * pxPerMm).roundToInt()
        val out = IntArray(w * h)
        val m = sheetToImage.m
        for (v in 0 until h) {
            val y = (v + 0.5) / pxPerMm
            for (u in 0 until w) {
                val x = (u + 0.5) / pxPerMm
                val d = m[6] * x + m[7] * y + m[8]
                val sx = (m[0] * x + m[1] * y + m[2]) / d
                val sy = (m[3] * x + m[4] * y + m[5]) / d
                out[v * w + u] = sampleArgb(src, sx, sy)
            }
        }
        return ArgbImage(w, h, out)
    }

    private fun sampleArgb(src: ArgbImage, x: Double, y: Double): Int {
        if (x < 0.0 || y < 0.0 || x > src.width - 1.0 || y > src.height - 1.0) return 0xFFFFFFFF.toInt()
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = min(x0 + 1, src.width - 1)
        val y1 = min(y0 + 1, src.height - 1)
        val fx = x - x0
        val fy = y - y0
        val p00 = src.pixels[y0 * src.width + x0]
        val p10 = src.pixels[y0 * src.width + x1]
        val p01 = src.pixels[y1 * src.width + x0]
        val p11 = src.pixels[y1 * src.width + x1]
        var out = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val top = ((p00 shr shift) and 0xFF) * (1 - fx) + ((p10 shr shift) and 0xFF) * fx
            val bottom = ((p01 shr shift) and 0xFF) * (1 - fx) + ((p11 shr shift) and 0xFF) * fx
            out = out or ((top * (1 - fy) + bottom * fy).roundToInt().coerceIn(0, 255) shl shift)
        }
        return out
    }
}
