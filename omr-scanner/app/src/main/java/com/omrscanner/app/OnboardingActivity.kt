package com.omrscanner.app

import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import kotlin.math.abs

/** Three short intro pages shown on first run (and from the ⋮ menu). */
class OnboardingActivity : BaseActivity() {
    private class Page(val icon: Icon, val tint: () -> Int, val bg: () -> Int, val title: String, val body: String)

    private val pages = listOf(
        Page(Icon.HOME, { Ui.ON_PRIMARY }, { Ui.PRIMARY }, "1 · Choose your OMR sheet",
            "On Home, pick a ready-made sheet if yours is exactly the same — or add any other sheet from a blank copy."),
        Page(Icon.KEY, { Ui.ON_PRIMARY_LIGHT }, { Ui.PRIMARY_LIGHT }, "2 · Set the answer key",
            "On Key, scan a sheet filled with the right answers (camera, photo or PDF), or type / tap them. One key per set if you like."),
        Page(Icon.SCAN, { Ui.ON_PRIMARY }, { Ui.PRIMARY }, "3 · Scan the sheets",
            "On Scanner, use the camera, a photo or a PDF. A PDF with many pages becomes one report."),
        Page(Icon.CHART, { Ui.ON_PRIMARY_LIGHT }, { Ui.PRIMARY_LIGHT }, "4 · See results",
            "On Results, every exam with its scores — export to Word, Excel or PDF."),
    )
    private var index = 0
    private lateinit var holder: FrameLayout
    private lateinit var dots: LinearLayout
    private lateinit var next: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = savedInstanceState?.getInt("page") ?: 0
        val root = vertical().apply { setBackgroundColor(Ui.BACKGROUND) }
        val top = horizontal().apply { setPadding(dp(16), dp(10), dp(8), 0) }
        top.add(View(this), weighted())
        top.add(button("Skip", ButtonStyle.TEXT) { done() }, wrap())
        root.add(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        holder = FrameLayout(this)
        root.add(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dots = horizontal().apply { gravity = Gravity.CENTER }
        root.add(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) })
        next = button("Next", ButtonStyle.PRIMARY) { if (index < pages.size - 1) go(index + 1) else done() }
        root.add(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(24), 0, dp(24), dp(28)) })
        setContentView(root)

        val swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < abs(vy)) return false
                if (vx < 0 && index < pages.size - 1) go(index + 1) else if (vx > 0 && index > 0) go(index - 1)
                return true
            }
        })
        holder.setOnTouchListener { _, e -> swipe.onTouchEvent(e) }
        go(index, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", index)
    }

    private fun go(i: Int, animate: Boolean = true) {
        val forward = i >= index
        index = i
        val p = pages[i]
        val page = vertical(32).apply { gravity = Gravity.CENTER }
        page.add(iconBadge(p.icon, p.tint(), p.bg(), 140, 64), LinearLayout.LayoutParams(dp(140), dp(140)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(36)
        })
        page.add(text(p.title, TextStyle.DISPLAY.sp - 4, Ui.TEXT, true).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        page.add(text(p.body, 16f, Ui.MUTED).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        holder.removeAllViews()
        holder.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (animate) {
            page.alpha = 0f
            page.translationX = dp(if (forward) 40 else -40).toFloat()
            page.animate().alpha(1f).translationX(0f).setDuration(220).start()
        }
        dots.removeAllViews()
        pages.indices.forEach { d ->
            dots.add(View(this).apply { background = rounded(if (d == i) Ui.PRIMARY else Ui.DIVIDER, dp(4).toFloat()) },
                LinearLayout.LayoutParams(dp(if (d == i) 24 else 8), dp(8)).apply { marginEnd = dp(6) })
        }
        next.text = if (i == pages.size - 1) "Get started" else "Next"
    }

    private fun done() {
        Store(this).onboarded = true
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (index > 0) go(index - 1) else done()
    }
}
