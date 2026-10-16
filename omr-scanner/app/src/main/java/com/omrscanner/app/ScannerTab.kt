package com.omrscanner.app

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable

/** Scanner: the selected OMR sheet, tips for good pictures, and scanning from camera, photo or PDF. */
class ScannerTab(private val a: MainActivity) {
    fun build(): View = with(a) {
        val (root, column) = tabPage("Scanner", iconButton(Icon.MORE) { appMenu() })
        column.add(HomeTab.selectedSheet(this), block(14))
        val ready = store.hasTemplate

        column.add(sectionHeader("Tips for a good result"), block(4))
        val tips = card(6)
        TIPS.forEachIndexed { i, (icon, title, lines) ->
            if (i > 0) tips.add(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(68) })
            tips.add(tipRow(icon, title, lines, i == openTip) { openTip = if (openTip == i) -1 else i; refresh() }, block(0))
        }
        column.add(tips, block(18))

        column.add(sectionHeader("Scan from"), block(4))
        column.add(source(Icon.CAMERA, "Camera", "Take a photo of the answer sheet", Ui.PRIMARY, ready) { scanSheet(true) }, block(10))
        column.add(source(Icon.GALLERY, "Photo", "Pick a picture or scan from the gallery", Ui.BLUE, ready) { scanSheet(false) }, block(10))
        column.add(source(Icon.PDF, "PDF", "One sheet, or many pages → a report sorted by roll", Ui.PURPLE, ready) { scanSheetPdf() }, block(0))
        if (!ready) column.add(text("Choose the OMR sheet on the Home tab to start scanning.", 13f, Ui.ORANGE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        }, block(0))
        root
    }

    private fun tipRow(icon: Icon, title: String, lines: List<String>, open: Boolean, onClick: () -> Unit): View = with(a) {
        val box = vertical().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ripple(rounded(android.graphics.Color.TRANSPARENT, dp(16).toFloat()), dp(16).toFloat())
            setOnClickListener { onClick() }
        }
        val head = horizontal()
        head.add(iconBadge(icon, Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT, 40, 20), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        head.add(text(title, 15f, Ui.TEXT, true), weighted())
        head.add(ImageView(this).apply {
            setImageDrawable(IconDrawable(Icon.CHEVRON, Ui.MUTED, dp(18)))
            rotation = if (open) 90f else 0f
        }, wrap())
        box.add(head, block(0))
        if (open) {
            val body = vertical().apply { setPadding(dp(54), dp(8), 0, 0) }
            for (l in lines) body.add(text("•  $l", 13.5f, Ui.MUTED), block(4))
            box.add(body, block(0))
        }
        box
    }

    private fun source(icon: Icon, title: String, sub: String, tint: Int, enabled: Boolean, onClick: () -> Unit): View = with(a) {
        val c = clickableCard(16) { if (enabled) onClick() else show(MainActivity.Tab.HOME) }
        c.alpha = if (enabled) 1f else 0.5f
        val row = horizontal()
        row.add(iconBadge(icon, tint, Ui.CARD_ALT, 52, 26), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
        val t = vertical()
        t.add(text(title, 17f, Ui.TEXT, true), wrap())
        t.add(text(sub, 13f, Ui.MUTED), wrap())
        row.add(t, weighted())
        row.add(ImageView(this).apply { setImageDrawable(IconDrawable(Icon.NEXT, Ui.MUTED, dp(22))) }, wrap())
        c.add(row, block(0))
        c
    }

    companion object {
        private var openTip = 0

        private val TIPS = listOf(
            Triple(Icon.CAMERA, "Camera", listOf(
                "Lay the sheet flat on a table; hold the phone straight above it.",
                "Keep all 4 black corner squares inside the photo.",
                "Let the sheet fill most of the frame; don't cut any edge.",
                "Tap to focus and hold still — no blur.",
            )),
            Triple(Icon.GALLERY, "Photo / scan", listOf(
                "Use the original picture, not a screenshot or a forwarded low-quality copy.",
                "Scanner apps (CamScanner, Google Drive scan, Adobe Scan) give the best pictures.",
                "JPG or PNG; the whole page must be visible.",
            )),
            Triple(Icon.PDF, "PDF", listOf(
                "One answer sheet per page.",
                "A PDF with many pages is checked page by page into a Word / Excel report.",
                "Scan at 200–300 dpi, colour or greyscale.",
            )),
            Triple(Icon.INFO, "Picture quality", listOf(
                "Even daylight or room light; avoid shadows of your hand or phone.",
                "No glare on the bubbles — tilt the sheet slightly if light reflects.",
                "Students should fill bubbles fully with a dark pen or 2B pencil.",
                "Sheets without corner squares: photograph on a darker surface so the page edges show.",
            )),
        )
    }
}
