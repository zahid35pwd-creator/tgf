package com.omrscanner.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable
import com.omrscanner.app.ui.ProgressRing
import java.util.Calendar

/** Dashboard: the active exam, the scan actions and the latest results. */
class HomeTab(private val a: MainActivity) {
    fun build(): View = with(a) {
        val s = store.settings
        val column = vertical().apply { setPadding(dp(16), dp(18), dp(16), dp(24)) }

        // Greeting header with the app mark.
        val header = horizontal().apply { setPadding(dp(4), 0, dp(4), dp(18)) }
        val hello = vertical()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        hello.add(text(greeting, TextStyle.CAPTION, Ui.MUTED), wrap())
        hello.add(text("OMR Scanner", TextStyle.DISPLAY.sp - 4, Ui.TEXT, true).apply { letterSpacing = -0.02f }, wrap())
        header.add(hello, weighted())
        header.add(iconBadge(Icon.SCAN, Ui.ON_PRIMARY, Ui.PRIMARY, 48, 24))
        column.add(header, block(0))

        column.add(examCard(s), block(16))
        column.add(heroButton(), block(12))

        val tiles = horizontal()
        tiles.add(tile(Icon.GALLERY, "From gallery", "Pick a scanned picture", Ui.BLUE, blueBg()) { scanSheet(false) },
            weighted().apply { marginEnd = dp(12) })
        tiles.add(tile(Icon.PDF, "Many sheets", "Check a whole PDF", Ui.PURPLE, Ui.PURPLE_BG) {
            startActivity(Intent(this, BatchActivity::class.java))
        }, weighted())
        column.add(tiles, block(22))

        val history = store.history()
        column.add(sectionHeader("Recent results", if (history.isNotEmpty()) "See all" else null) { show(MainActivity.Tab.RESULTS) }, block(4))
        if (history.isEmpty()) {
            val c = card(4)
            c.add(emptyState(Icon.CHART, "No results yet", "Scan a sheet and tap Save to keep its result here."), block(0))
            column.add(c, block(16))
        } else {
            val c = card(6)
            history.takeLast(3).reversed().forEachIndexed { i, r ->
                if (i > 0) c.add(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) })
                c.add(ResultsTab.resultRow(this, r) { show(MainActivity.Tab.RESULTS) }, block(0))
            }
            column.add(c, block(16))
        }

        column.add(sectionHeader("Tips for a good scan"), block(4))
        val tips = card(6)
        tips.add(listRow(Icon.SCAN, "Keep the whole sheet in view", "All 4 corner squares, sheet filling most of the photo", chevron = false), block(0))
        tips.add(listRow(Icon.INFO, "Flat and evenly lit", "Avoid strong shadows and glare", Ui.ORANGE, Ui.ORANGE_BG, chevron = false), block(0))
        tips.add(listRow(Icon.SHEET, "Any OMR sheet", "Teach a new design from a blank copy in Settings", Ui.GREEN, Ui.GREEN_BG, chevron = false) {
            show(MainActivity.Tab.SETTINGS)
        }, block(0))
        column.add(tips, block(0))

        ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Ui.BACKGROUND)
            addView(column)
        }
    }

    private fun blueBg() = if (Ui.dark) 0xFF172A45.toInt() else 0xFFE6F0FE.toInt()

    private fun examCard(s: ExamSettings): View = with(a) {
        val c = clickableCard(18) { show(MainActivity.Tab.SETTINGS) }
        val top = horizontal()
        val info = vertical()
        info.add(text("ACTIVE EXAM", TextStyle.OVERLINE, Ui.PRIMARY), block(4))
        info.add(text(s.examName.ifBlank { "Untitled exam" }, TextStyle.HEADING).apply { maxLines = 2 }, block(2))
        info.add(text(s.template.name, TextStyle.CAPTION, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, block(0))
        top.add(info, weighted())

        val keyed = store.storedKey(null).count { it != null }
        val sets = store.setsWithKeys()
        val ring = ProgressRing(this, Ui.CARD_ALT, if (keyed == s.questionCount || sets.isNotEmpty()) Ui.GREEN else Ui.PRIMARY, Ui.TEXT).apply {
            strokeDp = 6f
            label = if (sets.isNotEmpty() && keyed == 0) "${sets.size} set${if (sets.size > 1) "s" else ""}" else "$keyed"
            setProgress(if (sets.isNotEmpty() && keyed == 0) 1f else keyed.toFloat() / s.questionCount)
        }
        val ringBox = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        ringBox.add(ring, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(4) })
        ringBox.add(text(if (sets.isNotEmpty() && keyed == 0) "keyed" else "of ${s.questionCount} keyed", 11f, Ui.MUTED), wrap())
        ringBox.setOnClickListener { show(MainActivity.Tab.KEY) }
        top.add(ringBox, wrap().apply { marginStart = dp(12) })
        c.add(top, block(14))

        val chips = horizontal()
        chips.add(chip("${s.questionCount} questions"), wrap().apply { marginEnd = dp(6) })
        chips.add(chip(s.labelRange), wrap().apply { marginEnd = dp(6) })
        val marks = "+${fmt(s.perCorrect)}" + if (s.perWrong > 0) " / −${fmt(s.perWrong)}" else ""
        chips.add(chip(marks, Ui.GREEN, Ui.GREEN_BG), wrap())
        c.add(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, block(0))
        c
    }

    private fun heroButton(): View = with(a) {
        val r = dp(22).toFloat()
        val hero = horizontal().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            val grad = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.PRIMARY, if (Ui.dark) 0xFF6D5BF0.toInt() else 0xFF6D4AE8.toInt()))
            grad.cornerRadius = r
            background = ripple(grad, r, 0x33FFFFFF)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { scanSheet(true) }
        }
        hero.add(ImageView(this).apply {
            setImageDrawable(IconDrawable(Icon.CAMERA, Ui.ON_PRIMARY, dp(28)))
            scaleType = ImageView.ScaleType.CENTER
            background = rounded(0x33FFFFFF, dp(18).toFloat())
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(16) })
        val t = vertical()
        t.add(text("Scan a sheet", 20f, Ui.ON_PRIMARY, true), wrap())
        t.add(text("Take a photo of a filled answer sheet", 14f, Ui.ON_PRIMARY).apply { alpha = 0.85f }, wrap())
        hero.add(t, weighted())
        hero.add(ImageView(this).apply { setImageDrawable(IconDrawable(Icon.NEXT, Ui.ON_PRIMARY, dp(22))) }, wrap())
        hero
    }

    private fun tile(icon: Icon, title: String, sub: String, tint: Int, bg: Int, onClick: () -> Unit): View = with(a) {
        val c = clickableCard(16, onClick)
        c.add(iconBadge(icon, tint, bg, 42, 22), LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(12) })
        c.add(text(title, 15f, Ui.TEXT, true), wrap())
        c.add(text(sub, 12.5f, Ui.MUTED), wrap())
        c.setBackgroundColor(Color.TRANSPARENT)
        val radius = dp(20).toFloat()
        c.background = ripple(rounded(Ui.CARD, radius, if (Ui.dark) Ui.DIVIDER else 0, if (Ui.dark) dp(1) else 0), radius)
        c
    }
}
