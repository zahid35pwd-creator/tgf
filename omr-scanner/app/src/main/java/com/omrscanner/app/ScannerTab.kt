package com.omrscanner.app

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable

/** Scanner: the selected OMR sheet and three ways to scan, each with its own tips. */
class ScannerTab(private val a: MainActivity) {
    private class Source(val icon: Icon, val title: String, val sub: String, val action: String, val tips: List<String>, val go: (MainActivity) -> Unit)

    fun build(): View = with(a) {
        val (root, column) = tabPage("Scanner", iconButton(Icon.MORE) { appMenu() })
        column.add(HomeTab.selectedSheet(this), block(16))
        val ready = store.hasTemplate
        column.add(sectionHeader("Scan from"), block(4))
        SOURCES.forEachIndexed { i, src -> column.add(sourceCard(i, src, ready), block(12)) }
        if (!ready) column.add(text("Choose the OMR sheet on the Home tab to start scanning.", 13f, Ui.ORANGE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
        }, block(0))
        root
    }

    private fun sourceCard(i: Int, src: Source, ready: Boolean): View = with(a) {
        val c = card(16)
        val row = horizontal()
        row.add(iconBadge(src.icon, Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT, 48, 24), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text(src.title, 17f, Ui.TEXT, true), wrap())
        t.add(text(src.sub, 13f, Ui.MUTED), wrap())
        row.add(t, weighted())
        c.add(row, block(12))

        // Tips for this source, folded by default except the first.
        val open = openTip == i
        val tipsHead = horizontal().apply {
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = ripple(rounded(android.graphics.Color.TRANSPARENT, dp(10).toFloat()), dp(10).toFloat())
            setOnClickListener { openTip = if (open) -1 else i; refresh() }
        }
        tipsHead.add(text("Tips for a good result", 13.5f, Ui.PRIMARY, true), weighted())
        tipsHead.add(ImageView(this).apply {
            setImageDrawable(IconDrawable(Icon.CHEVRON, Ui.PRIMARY, dp(16)))
            rotation = if (open) 90f else 0f
        }, wrap())
        c.add(tipsHead, block(if (open) 6 else 10))
        if (open) {
            val body = vertical().apply { setPadding(dp(8), 0, dp(4), dp(8)) }
            for (l in src.tips) body.add(text("•  $l", 13.5f, Ui.MUTED), block(5))
            c.add(body, block(6))
        }

        c.add(button(src.action, ButtonStyle.PRIMARY, src.icon) { if (ready) src.go(this) else show(MainActivity.Tab.HOME) }.apply {
            alpha = if (ready) 1f else 0.5f
        }, block(0))
        c
    }

    companion object {
        private var openTip = 0

        private val QUALITY = listOf(
            "Even daylight or room light; no shadow of your hand or phone.",
            "No glare on the bubbles — tilt the sheet slightly if light reflects.",
        )

        private val SOURCES = listOf(
            Source(Icon.CAMERA, "Camera", "Take a photo of the answer sheet", "Open camera", listOf(
                "Lay the sheet flat; hold the phone straight above it.",
                "Keep all 4 black corner squares inside the photo; don't cut any edge.",
                "Let the sheet fill most of the frame.",
                "Tap to focus and hold still — no blur.",
            ) + QUALITY + "Sheets without corner squares: photograph on a darker surface so the page edges show.") { it.scanSheet(true) },
            Source(Icon.GALLERY, "Photo", "Pick a picture or scan from the gallery", "Choose photo", listOf(
                "Use the original picture, not a screenshot or a compressed forwarded copy.",
                "Scanner apps (Google Drive scan, Adobe Scan, CamScanner) give the best pictures.",
                "The whole page must be visible, sharp and well lit.",
                "Next you can check the orientation and turn the picture if needed.",
            ) + QUALITY) { it.scanSheet(false) },
            Source(Icon.PDF, "PDF", "One sheet, or many pages → one report", "Choose PDF", listOf(
                "One answer sheet per page.",
                "A PDF with many pages is checked page by page into a report sorted by roll.",
                "Scan at 200–300 dpi, colour or greyscale.",
                "Pages must be upright.",
            )) { it.scanSheetPdf() },
        )
    }
}
