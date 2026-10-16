package com.omrscanner.app

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable
import com.omrscanner.app.ui.ScanAnimationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The scanner intake landing: pipeline tiles, single/batch toggle, big CAPTURE & SCORE. */
class ScannerTab(private val a: MainActivity) {
    private enum class Mode { SINGLE, BATCH }
    private var mode = Mode.SINGLE
    private enum class Source { CAM, GALLERY, PDF, ZIP }
    private var source = Source.CAM

    fun build(): View = with(a) {
        val more = iconButton(Icon.MORE) { overflow() }
        val (root, column) = tabPage("Scanner", more)
        column.add(statusStrip("OMR_SENSOR READY  //  TILT: 0.3°  //  LIGHT: OPTIMAL", DotColor.OK), block(14))

        column.add(previewCard(), block(14))
        column.add(lockInfoCard(), block(18))

        column.add(monoText("INTAKE_PIPELINE  //  SELECT ENGINE", 11.5f, Ui.PRIMARY).apply { setPadding(dp(4), 0, 0, dp(8)) }, wrap())
        val pipeline = horizontal()
        pipeline.add(pipelineTile(Icon.CAMERA, "LIVE CAM", Source.CAM), weighted().apply { marginEnd = dp(8) })
        pipeline.add(pipelineTile(Icon.GALLERY, "GALLERY", Source.GALLERY), weighted().apply { marginEnd = dp(8) })
        pipeline.add(pipelineTile(Icon.PDF, "DOC PDF", Source.PDF), weighted().apply { marginEnd = dp(8) })
        pipeline.add(pipelineTile(Icon.IMPORT, "BATCH ZIP", Source.ZIP), weighted())
        column.add(pipeline, block(14))

        val toggle = horizontal().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); background = rounded(Ui.CARD_ALT, dp(14).toFloat(), Ui.DIVIDER, dp(1)) }
        toggle.add(modeTile("SINGLE KEY MASTER", Mode.SINGLE), weighted())
        toggle.add(modeTile("BATCH  //  50 SHEETS", Mode.BATCH), weighted())
        column.add(toggle, block(14))

        column.add(captureCta(), block(20))

        column.add(monoText("DETECTED_ON_DEVICE", 11.5f, Ui.PRIMARY).apply { setPadding(dp(4), 0, 0, dp(8)) }, wrap())
        column.add(recentCard(), block(10))
        column.add(templateCard(), block(0))
        root
    }

    private fun previewCard(): View = with(a) {
        val c = card(0).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val stack = FrameLayout(this)
        // The scanning illustration doubles as the "camera preview" when no live feed exists.
        stack.addView(ScanAnimationView(this, Ui.CARD, Ui.MUTED, Ui.PRIMARY),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        // Four corner-lock chips over the preview.
        stack.addView(cornerChip("TL LOCKED"), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        stack.addView(cornerChip("TR LOCKED"), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(12), 0) })
        stack.addView(cornerChip("BL LOCKED"), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(12), 0, 0, dp(12)) })
        stack.addView(cornerChip("BR LOCKED"), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(12), dp(12)) })
        c.add(stack, block(8))
        val footer = horizontal().apply { setPadding(dp(10), 0, dp(10), 0) }
        footer.add(ledDot(DotColor.OK), LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        footer.add(monoText("GEOMETRY CALIBRATED", 11.5f, Ui.ON_PRIMARY_LIGHT), weighted())
        footer.add(monoText("CONFIDENCE: 99.8%", 11.5f, Ui.ON_PRIMARY_LIGHT), wrap())
        c.add(footer, block(0))
        c
    }

    private fun cornerChip(label: String) = a.monoText(label, 10f, Ui.ON_PRIMARY_LIGHT).apply {
        setPadding(a.dp(8), a.dp(4), a.dp(8), a.dp(4))
        background = rounded(0xCC13181B.toInt(), a.dp(4).toFloat(), Ui.ON_PRIMARY_LIGHT, a.dp(1))
    }

    private fun lockInfoCard(): View = with(a) {
        val c = card(16)
        val row = horizontal()
        row.add(iconBadge(Icon.LOCK, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 40, 20),
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text("4-Corner Fiducial Lock", 15f, Ui.TEXT, true), wrap())
        t.add(text("Keep the camera parallel to the desk. All 4 black anchor boxes must stay in frame for instant homography scoring.", 12.5f, Ui.MUTED), wrap())
        row.add(t, weighted())
        c.add(row, block(0))
        c
    }

    private fun pipelineTile(icon: Icon, label: String, src: Source): View = with(a) {
        val on = src == source
        val tile = vertical().apply {
            setPadding(dp(10), dp(14), dp(10), dp(14))
            gravity = Gravity.CENTER_HORIZONTAL
            val r = dp(14).toFloat()
            background = ripple(rounded(Ui.CARD, r, if (on) Ui.PRIMARY else Ui.DIVIDER, dp(if (on) 1.5f else 1f)), r)
            isClickable = true
            setOnClickListener { source = src; a.refresh() }
        }
        tile.add(ImageView(this).apply { setImageDrawable(IconDrawable(icon, if (on) Ui.PRIMARY else Ui.MUTED, dp(22))); scaleType = ImageView.ScaleType.CENTER },
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(6) })
        tile.add(monoText(label, 10f, if (on) Ui.TEXT else Ui.MUTED).apply { gravity = Gravity.CENTER }, wrap())
        return tile
    }

    private fun modeTile(label: String, m: Mode): View = with(a) {
        val on = m == mode
        return monoText(label, 11f, if (on) Ui.ON_PRIMARY else Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(11), dp(8), dp(11))
            val r = dp(10).toFloat()
            background = ripple(rounded(if (on) Ui.PRIMARY else Color.TRANSPARENT, r), r)
            setOnClickListener { if (!on) { mode = m; a.refresh() } }
        }
    }

    private fun captureCta(): View = with(a) {
        val r = dp(16).toFloat()
        val cta = horizontal().apply {
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = ripple(rounded(Ui.PRIMARY, r), r, 0x33000000)
            isClickable = true
            setOnClickListener { fire() }
            elevation = dp(2).toFloat()
        }
        cta.add(ImageView(this).apply {
            setImageDrawable(IconDrawable(Icon.CAMERA, Ui.ON_PRIMARY, dp(26)))
            scaleType = ImageView.ScaleType.CENTER
            background = rounded(0x33000000, dp(14).toFloat())
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text(if (mode == Mode.SINGLE) "CAPTURE & SCORE" else "CAPTURE BATCH", 17f, Ui.ON_PRIMARY, true).apply { letterSpacing = 0.04f }, wrap())
        t.add(monoText("SHUTTER_ACTUATE  //  OPENS ${sourceLabel()}", 11f, Ui.ON_PRIMARY).apply { alpha = 0.75f }, wrap())
        cta.add(t, weighted())
        cta.add(ImageView(this).apply { setImageDrawable(IconDrawable(Icon.NEXT, Ui.ON_PRIMARY, dp(22))) }, wrap())
        cta
    }

    private fun sourceLabel() = when (source) {
        Source.CAM -> "SYSTEM CAMERA"
        Source.GALLERY -> "GALLERY PICKER"
        Source.PDF -> "PDF OPENER"
        Source.ZIP -> "BATCH PICKER"
    }

    private fun fire() = with(a) {
        when (mode) {
            Mode.BATCH -> startActivity(Intent(this, BatchActivity::class.java))
            Mode.SINGLE -> when (source) {
                Source.CAM -> scanSheet(true)
                Source.GALLERY -> scanSheet(false)
                Source.PDF, Source.ZIP -> startActivity(Intent(this, BatchActivity::class.java))
            }
        }
    }

    private fun recentCard(): View = with(a) {
        val last = store.history().lastOrNull()
        val c = card(14)
        val row = horizontal()
        row.add(iconBadge(Icon.SHEET, Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT, 44, 22),
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(monoText(if (last == null) "NO RECENT CAPTURE" else "RECENT_SNAPSHOT", 11.5f, Ui.PRIMARY), wrap())
        if (last == null) {
            t.add(text("Your last scan will show up here", 13.5f, Ui.MUTED), wrap())
        } else {
            t.add(text("Roll ${last.roll.ifBlank { "?" }}  ·  ${fmt(last.score)}/${fmt(last.maxScore)}", 14.5f, Ui.TEXT, true), wrap())
            val when_ = SimpleDateFormat("d MMM, HH:mm", Locale.US).format(Date(last.time))
            t.add(monoText(when_, 11f, Ui.MUTED), wrap())
        }
        row.add(t, weighted())
        if (last != null) row.add(a.monoChip("OPEN"), wrap().apply { marginStart = dp(8) })
        c.add(row, block(0))
        return c
    }

    private fun templateCard(): View = with(a) {
        val t = store.settings.template
        val c = card(14)
        val row = horizontal()
        row.add(iconBadge(Icon.FIDUCIAL, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 44, 22),
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val col = vertical()
        col.add(monoText("STANDARDIZED_TEMPLATE  //  ACTIVE", 11.5f, Ui.PRIMARY), wrap())
        col.add(text(t.name, 14.5f, Ui.TEXT, true), wrap())
        col.add(monoText("${t.maxQuestions} QUESTIONS  //  ${t.maxOptions}-OPT", 11f, Ui.MUTED), wrap())
        row.add(col, weighted())
        row.add(a.monoChip("CHANGE").apply { setOnClickListener { show(MainActivity.Tab.SETTINGS) } }, wrap().apply { marginStart = dp(8) })
        c.add(row, block(0))
        return c
    }

    private fun overflow() = a.bottomSheet("Scanner") { col, d ->
        with(a) {
            col.add(listRow(Icon.PRINT, "Print blank sheet", null) { d.dismiss(); SheetPrinter.print(this, store.settings.sheetSpec) }, block(0))
            col.add(listRow(Icon.SPARK, "Show introduction", null) { d.dismiss(); startActivity(Intent(this, OnboardingActivity::class.java)) }, block(0))
        }
    }
}
