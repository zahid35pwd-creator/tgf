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
    fun rectify(src: ArgbImage, sheetToImage: Homography, pxPerMm: Double): ArgbImage {
        val w = (SheetLayout.PAGE_WIDTH * pxPerMm).roundToInt()
        val h = (SheetLayout.PAGE_HEIGHT * pxPerMm).roundToInt()
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
