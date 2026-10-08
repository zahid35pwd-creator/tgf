package com.omrscanner.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Draws the sheet with AWT, in millimetres scaled to [pxPerMm]. */
class AwtSheetCanvas(private val g: Graphics2D, private val pxPerMm: Double) : SheetCanvas {
    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.scale(pxPerMm, pxPerMm)
    }

    override fun fillRect(left: Double, top: Double, width: Double, height: Double, color: Int) {
        g.color = Color(color, true); g.fill(Rectangle2D.Double(left, top, width, height))
    }

    override fun strokeRect(left: Double, top: Double, width: Double, height: Double, stroke: Double, color: Int) {
        g.color = Color(color, true); g.stroke = BasicStroke(stroke.toFloat()); g.draw(Rectangle2D.Double(left, top, width, height))
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) {
        g.color = Color(color, true); g.fill(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
    }

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Double, color: Int) {
        g.color = Color(color, true); g.stroke = BasicStroke(stroke.toFloat()); g.draw(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Double, color: Int) {
        g.color = Color(color, true); g.stroke = BasicStroke(stroke.toFloat()); g.draw(Line2D.Double(x1, y1, x2, y2))
    }

    override fun text(text: String, x: Double, baseline: Double, size: Double, align: TextAlign, bold: Boolean, color: Int) {
        // Fonts are laid out at 10x size then scaled down, because AWT handles sub-unit font sizes poorly.
        val old = g.transform
        g.translate(x, baseline)
        g.scale(0.1, 0.1)
        g.font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont((size * 10).toFloat())
        val w = g.fontMetrics.stringWidth(text)
        val dx = when (align) { TextAlign.LEFT -> 0; TextAlign.CENTER -> -w / 2; TextAlign.RIGHT -> -w }
        g.color = Color(color, true)
        g.drawString(text, dx.toFloat(), 0f)
        g.transform = old
    }
}

enum class Ink(val rgb: Int, val alpha: Int) { BLACK_PEN(0x18181C, 245), BLUE_PEN(0x1C2C8C, 235), PENCIL(0x55565C, 200) }

/** A filled-in sheet: which answer bubbles and roll digits the "student" marked. */
class FilledSheet(
    val spec: SheetSpec,
    val answers: List<List<Int>>,
    val roll: List<Int?>,
    val ink: Ink = Ink.BLACK_PEN,
    /** Extra light marks (question, option) that simulate erased answers. */
    val erased: List<Pair<Int, Int>> = emptyList(),
)

object SyntheticSheets {

    fun render(sheet: FilledSheet, pxPerMm: Double, rng: Random, handwriting: Boolean = true): BufferedImage {
        val img = BufferedImage((SheetLayout.PAGE_WIDTH * pxPerMm).roundToInt(), (SheetLayout.PAGE_HEIGHT * pxPerMm).roundToInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        SheetPainter.paint(AwtSheetCanvas(g, pxPerMm), sheet.spec)
        g.dispose()
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.scale(pxPerMm, pxPerMm)
        sheet.answers.forEachIndexed { q, opts -> for (o in opts) scribble(g2, SheetLayout.answerBubble(q, o), SheetLayout.ANSWER_RADIUS, sheet.ink, rng) }
        sheet.roll.forEachIndexed { pos, d ->
            if (d == null) return@forEachIndexed
            scribble(g2, SheetLayout.rollBubble(pos, d), SheetLayout.ROLL_RADIUS, sheet.ink, rng)
            g2.color = Color(sheet.ink.rgb)
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(4.5f)
            g2.drawString(d.toString(), (SheetLayout.ROLL_LEFT + pos * SheetLayout.ROLL_COLUMN_PITCH - 1.3 + rng.nextDouble(-0.5, 0.5)).toFloat(), 34.8f)
        }
        for ((q, o) in sheet.erased) {
            val c = SheetLayout.answerBubble(q, o)
            g2.color = Color(0x80, 0x80, 0x84, 90)
            g2.fill(Ellipse2D.Double(c.x - 2.0, c.y - 2.0, 4.0, 4.0))
        }
        if (handwriting) {
            g2.color = Color(sheet.ink.rgb)
            g2.font = Font(Font.SERIF, Font.ITALIC, 1).deriveFont(5f)
            g2.drawString("Student ${rng.nextInt(100)}", 48f, 31f)
        }
        g2.dispose()
        return img
    }

    /** A hand-filled bubble: an imperfect ellipse with pen strokes and a few gaps. */
    private fun scribble(g: Graphics2D, c: Pt, r: Double, ink: Ink, rng: Random) {
        val color = Color((ink.rgb shr 16) and 0xFF, (ink.rgb shr 8) and 0xFF, ink.rgb and 0xFF, ink.alpha)
        g.color = color
        val ox = c.x + rng.nextDouble(-0.3, 0.3)
        val oy = c.y + rng.nextDouble(-0.3, 0.3)
        val rx = r * rng.nextDouble(0.82, 1.08)
        val ry = r * rng.nextDouble(0.82, 1.08)
        g.fill(Ellipse2D.Double(ox - rx, oy - ry, 2 * rx, 2 * ry))
        g.stroke = BasicStroke(0.35f)
        repeat(6) {
            val a = rng.nextDouble(0.0, PI)
            g.draw(Line2D.Double(ox - cos(a) * r, oy - sin(a) * r, ox + cos(a) * r, oy + sin(a) * r))
        }
        g.color = Color(255, 255, 255, 150)
        repeat(rng.nextInt(0, 4)) {
            val x = ox + rng.nextDouble(-rx, rx) * 0.6
            val y = oy + rng.nextDouble(-ry, ry) * 0.6
            g.fill(Ellipse2D.Double(x - 0.25, y - 0.25, 0.5, 0.5))
        }
    }

    class PhotoParams(
        val width: Int = 3000,
        val height: Int = 4000,
        /** Page rotation in degrees (any value). */
        val rotation: Double = 0.0,
        /** Fraction of the frame the page's long side covers. */
        val coverage: Double = 0.85,
        /** Corner jitter as a fraction of page size (perspective). */
        val perspective: Double = 0.05,
        /** Max page bending displacement, mm. */
        val curl: Double = 0.0,
        val shading: Double = 0.3,
        val shadow: Boolean = false,
        val noise: Double = 4.0,
        val blurRadius: Int = 1,
        val jpegQuality: Float = 0.8f,
        val background: Int = 0x7A5A3C,
    )

    fun randomPhotoParams(rng: Random, rotationBase: Double = listOf(0.0, 90.0, 180.0, 270.0).random(rng)): PhotoParams {
        val portrait = rng.nextBoolean()
        val (w, h) = listOf(4000 to 3000, 2000 to 1500, 1600 to 1200, 3264 to 2448).random(rng)
        return PhotoParams(
            width = if (portrait) h else w,
            height = if (portrait) w else h,
            rotation = rotationBase + rng.nextDouble(-12.0, 12.0),
            coverage = rng.nextDouble(0.6, 0.95),
            perspective = rng.nextDouble(0.0, 0.08),
            curl = rng.nextDouble(0.0, 1.5),
            shading = rng.nextDouble(0.0, 0.45),
            shadow = rng.nextInt(3) == 0,
            noise = rng.nextDouble(1.0, 9.0),
            blurRadius = rng.nextInt(0, 3),
            jpegQuality = rng.nextDouble(0.5, 0.92).toFloat(),
            background = listOf(0x7A5A3C, 0x3A3A40, 0xC8C6C0, 0x9AA4AE, 0x202020).random(rng),
        )
    }

    /** Simulates photographing [page] (rendered at [pxPerMm]) on a desk. */
    fun photograph(page: BufferedImage, pxPerMm: Double, p: PhotoParams, rng: Random): BufferedImage {
        val pw = page.width.toDouble()
        val ph = page.height.toDouble()
        val pagePx = page.getRGB(0, 0, page.width, page.height, null, 0, page.width)

        // Where the page corners land in the photo.
        val a = p.rotation * PI / 180
        val rotW = Math.abs(pw * cos(a)) + Math.abs(ph * sin(a))
        val rotH = Math.abs(pw * sin(a)) + Math.abs(ph * cos(a))
        val scale = p.coverage * min(p.width / rotW, p.height / rotH)
        val cx = p.width / 2.0 + rng.nextDouble(-0.03, 0.03) * p.width
        val cy = p.height / 2.0 + rng.nextDouble(-0.03, 0.03) * p.height
        val src = listOf(Pt(0.0, 0.0), Pt(pw, 0.0), Pt(pw, ph), Pt(0.0, ph))
        val dst = src.map { s ->
            val x = (s.x - pw / 2 + rng.nextDouble(-p.perspective, p.perspective) * pw) * scale
            val y = (s.y - ph / 2 + rng.nextDouble(-p.perspective, p.perspective) * ph) * scale
            Pt(cx + x * cos(a) - y * sin(a), cy + x * sin(a) + y * cos(a))
        }
        val inv = Homography.fromPoints(dst, src)!!.m

        val out = IntArray(p.width * p.height)
        val bgR = (p.background shr 16) and 0xFF
        val bgG = (p.background shr 8) and 0xFF
        val bgB = p.background and 0xFF
        val curlPx = p.curl * pxPerMm
        val shadeAngle = rng.nextDouble(0.0, 2 * PI)
        val shadowX = rng.nextDouble(0.2, 0.8) * p.width
        val shadowY = rng.nextDouble(0.2, 0.8) * p.height
        val shadowR = 0.25 * min(p.width, p.height)
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val d = inv[6] * x + inv[7] * y + inv[8]
            var u = (inv[0] * x + inv[1] * y + inv[2]) / d
            var v = (inv[3] * x + inv[4] * y + inv[5]) / d
            var r: Double
            var g: Double
            var b: Double
            if (u >= 0 && v >= 0 && u < pw - 1 && v < ph - 1) {
                // Bent paper: smooth displacement that vanishes at the corners.
                u += curlPx * sin(PI * v / ph) * sin(PI * u / pw)
                v += curlPx * 0.6 * sin(2 * PI * u / pw) * sin(PI * v / ph)
                u = u.coerceIn(0.0, pw - 1.001)
                v = v.coerceIn(0.0, ph - 1.001)
                val c = bilinear(pagePx, page.width, u, v)
                r = ((c shr 16) and 0xFF).toDouble(); g = ((c shr 8) and 0xFF).toDouble(); b = (c and 0xFF).toDouble()
            } else {
                val t = 0.85 + 0.15 * sin(x * 0.013 + sin(y * 0.002) * 6)
                r = bgR * t; g = bgG * t; b = bgB * t
            }
            val nx = x.toDouble() / p.width - 0.5
            val ny = y.toDouble() / p.height - 0.5
            var light = 1.0 - p.shading * (0.5 + nx * cos(shadeAngle) + ny * sin(shadeAngle))
            if (p.shadow) {
                val dx = x - shadowX
                val dy = y - shadowY
                light *= 1.0 - 0.4 * exp(-(dx * dx + dy * dy) / (2 * shadowR * shadowR))
            }
            light *= 0.92
            out[y * p.width + x] = rgb(r * light, g * light, b * light)
        }
        var img = BufferedImage(p.width, p.height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, p.width, p.height, if (p.blurRadius > 0) boxBlur(out, p.width, p.height, p.blurRadius) else out, 0, p.width)
        addNoise(img, p.noise, rng)
        img = jpeg(img, p.jpegQuality)
        return img
    }

    fun toArgb(img: BufferedImage) = ArgbImage(img.width, img.height, img.getRGB(0, 0, img.width, img.height, null, 0, img.width))

    private fun rgb(r: Double, g: Double, b: Double) =
        (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)

    private fun bilinear(px: IntArray, w: Int, x: Double, y: Double): Int {
        val x0 = x.toInt(); val y0 = y.toInt()
        val fx = x - x0; val fy = y - y0
        val p00 = px[y0 * w + x0]; val p10 = px[y0 * w + x0 + 1]
        val p01 = px[(y0 + 1) * w + x0]; val p11 = px[(y0 + 1) * w + x0 + 1]
        var out = 0xFF shl 24
        for (s in intArrayOf(16, 8, 0)) {
            val t = ((p00 shr s) and 0xFF) * (1 - fx) + ((p10 shr s) and 0xFF) * fx
            val bt = ((p01 shr s) and 0xFF) * (1 - fx) + ((p11 shr s) and 0xFF) * fx
            out = out or ((t * (1 - fy) + bt * fy).roundToInt() shl s)
        }
        return out
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int): IntArray {
        fun pass(src: IntArray, horizontal: Boolean): IntArray {
            val dst = IntArray(src.size)
            for (y in 0 until h) for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (k in -r..r) {
                    val xx = if (horizontal) x + k else x
                    val yy = if (horizontal) y else y + k
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val c = src[yy * w + xx]
                    sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF; n++
                }
                dst[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
            return dst
        }
        return pass(pass(px, true), false)
    }

    private fun addNoise(img: BufferedImage, sigma: Double, rng: Random) {
        val px = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        val jr = java.util.Random(rng.nextLong())
        for (i in px.indices) {
            val n = jr.nextGaussian() * sigma
            val c = px[i]
            px[i] = rgb(((c shr 16) and 0xFF) + n, ((c shr 8) and 0xFF) + n, (c and 0xFF) + n)
        }
        img.setRGB(0, 0, img.width, img.height, px, 0, img.width)
    }

    private fun jpeg(img: BufferedImage, quality: Float): BufferedImage {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val bytes = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bytes).use { out ->
            writer.output = out
            val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
            writer.write(null, IIOImage(img, null, null), param)
        }
        writer.dispose()
        return ImageIO.read(ByteArrayInputStream(bytes.toByteArray()))
    }
}
