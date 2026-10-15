package com.omrscanner.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** A circular progress ring with a centred label. */
class ProgressRing(context: Context, private val track: Int, private val color: Int, private val textColor: Int) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private var shown = 0f
    var label = ""
        set(v) { field = v; invalidate() }
    var strokeDp = 8f

    /** Sets the filled fraction (0–1), animating from the current value. */
    fun setProgress(fraction: Float, animate: Boolean = true) {
        val target = fraction.coerceIn(0f, 1f)
        if (!animate) { shown = target; invalidate(); return }
        ValueAnimator.ofFloat(shown, target).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val sw = strokeDp * density
        val size = min(width, height) - sw
        val rect = RectF((width - size) / 2f, (height - size) / 2f, (width + size) / 2f, (height + size) / 2f)
        trackPaint.strokeWidth = sw; trackPaint.color = track
        arcPaint.strokeWidth = sw; arcPaint.color = color
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (shown > 0f) canvas.drawArc(rect, -90f, 360f * shown, false, arcPaint)
        if (label.isNotEmpty()) {
            labelPaint.color = textColor
            labelPaint.textSize = size * if (label.length > 4) 0.22f else 0.28f
            canvas.drawText(label, width / 2f, height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2, labelPaint)
        }
    }
}

/** Horizontal score-distribution bars: one row per band with a label and a count. */
class BarChart(context: Context, private val bars: List<Triple<String, Int, Int>>, private val track: Int, private val textColor: Int, private val muted: Int) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f * density }
    private var progress = 0f

    init {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private val rowHeight get() = 26 * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (bars.size * rowHeight).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val labelW = 64 * density
        val countW = 32 * density
        val barH = 10 * density
        val maxCount = max(1, bars.maxOfOrNull { it.second } ?: 1)
        bars.forEachIndexed { i, (label, count, color) ->
            val cy = i * rowHeight + rowHeight / 2
            textPaint.color = muted
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, 0f, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
            val left = labelW
            val right = width - countW
            paint.color = track
            canvas.drawRoundRect(RectF(left, cy - barH / 2, right, cy + barH / 2), barH / 2, barH / 2, paint)
            val w = (right - left) * count / maxCount * progress
            if (count > 0) {
                paint.color = color
                canvas.drawRoundRect(RectF(left, cy - barH / 2, left + max(w, barH), cy + barH / 2), barH / 2, barH / 2, paint)
            }
            textPaint.color = textColor
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(count.toString(), width.toFloat(), cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
    }
}

/** Shows a bitmap fitted to the view; pinch or double-tap to zoom, drag to pan. */
class ZoomImageView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var baseScale = 1f
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f

    /** Height relative to width when measured with a free height (defaults to the image's aspect). */
    var maxHeightPx = Int.MAX_VALUE

    fun setImage(bmp: Bitmap?) {
        bitmap = bmp
        requestLayout()
        reset()
    }

    private fun reset() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        baseScale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        scale = 1f
        tx = (width - b.width * baseScale) / 2
        ty = (height - b.height * baseScale) / 2
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val b = bitmap
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec)
        else if (b == null) w else min(maxHeightPx, (w.toLong() * b.height / b.width).toInt())
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = reset()

    private fun clamp() {
        val b = bitmap ?: return
        val s = baseScale * scale
        val iw = b.width * s
        val ih = b.height * s
        tx = if (iw <= width) (width - iw) / 2 else tx.coerceIn(width - iw, 0f)
        ty = if (ih <= height) (height - ih) / 2 else ty.coerceIn(height - ih, 0f)
    }

    private fun zoomTo(newScale: Float, fx: Float, fy: Float) {
        val ns = newScale.coerceIn(1f, 6f)
        val k = ns / scale
        tx = fx - (fx - tx) * k
        ty = fy - (fy - ty) * k
        scale = ns
        clamp()
        invalidate()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomTo(scale * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale <= 1.01f) return false
            tx -= dx; ty -= dy
            clamp()
            invalidate()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val from = scale
            val to = if (scale > 1.5f) 1f else 2.5f
            ValueAnimator.ofFloat(from, to).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { zoomTo(it.animatedValue as Float, e.x, e.y) }
                start()
            }
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        // While zoomed in, keep the gesture from the scrolling page around us.
        parent?.requestDisallowInterceptTouchEvent(scale > 1.01f || event.pointerCount > 1)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val s = baseScale * scale
        matrix.reset()
        matrix.postScale(s, s)
        matrix.postTranslate(tx, ty)
        canvas.drawBitmap(b, matrix, paint)
    }
}

/** Counts a number up in a TextView, formatting each frame with [format]. */
fun TextView.countUp(target: Double, format: (Double) -> String) {
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        interpolator = DecelerateInterpolator()
        addUpdateListener { text = format(target * (it.animatedValue as Float)) }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { text = format(target) }
        })
        start()
    }
}

/** A sheet illustration with a scanning line sweeping over it, for "reading…" screens. */
class ScanAnimationView(context: Context, private val paper: Int, private val ink: Int, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sheetW = min(w * 0.62f, h * 0.7f)
        val sheetH = sheetW * 1.3f
        val l = (w - sheetW) / 2
        val t = (h - sheetH) / 2
        val d = sheetW / 100
        paint.style = Paint.Style.FILL
        paint.color = paper
        canvas.drawRoundRect(RectF(l, t, l + sheetW, t + sheetH), 6 * d, 6 * d, paint)
        paint.color = ink
        val m = 9 * d
        for ((x, y) in listOf(l + 6 * d to t + 6 * d, l + sheetW - 6 * d - m to t + 6 * d, l + 6 * d to t + sheetH - 6 * d - m, l + sheetW - 6 * d - m to t + sheetH - 6 * d - m)) {
            canvas.drawRoundRect(RectF(x, y, x + m, y + m), 1.5f * d, 1.5f * d, paint)
        }
        // Bubble rows: a few filled.
        paint.strokeWidth = 1.6f * d
        for (r in 0 until 8) for (o in 0 until 4) {
            val cx = l + 30 * d + o * 14 * d
            val cy = t + 26 * d + r * 11.5f * d
            val filled = (r * 7 + 3) % 4 == o
            paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            paint.color = if (filled) accent else ink
            paint.alpha = if (filled) 255 else 90
            canvas.drawCircle(cx, cy, 4 * d, paint)
        }
        paint.alpha = 255
        // Scanning line with a soft glow.
        val y = t + 4 * d + (sheetH - 8 * d) * phase
        paint.style = Paint.Style.FILL
        paint.shader = android.graphics.LinearGradient(0f, y - 14 * d, 0f, y, 0x00000000, (accent and 0x00FFFFFF) or 0x55000000, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l - 4 * d, y - 14 * d, l + sheetW + 4 * d, y, paint)
        paint.shader = null
        paint.color = accent
        canvas.drawRoundRect(RectF(l - 6 * d, y - 1.2f * d, l + sheetW + 6 * d, y + 1.2f * d), d, d, paint)
    }
}
