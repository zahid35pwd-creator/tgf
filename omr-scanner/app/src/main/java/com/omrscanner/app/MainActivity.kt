package com.omrscanner.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable

/** The app's home: four tabs (Home, Key, Scanner, Results) under a bottom navigation bar. */
class MainActivity : BaseActivity() {
    enum class Tab(val label: String, val icon: Icon) {
        HOME("Home", Icon.HOME),
        KEY("Key", Icon.KEY),
        SCANNER("Scanner", Icon.SCAN),
        RESULTS("Results", Icon.CHART),
    }

    lateinit var store: Store
        private set
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private var tab = Tab.HOME

    /** Set while a picture is being taken for the answer key rather than for grading. */
    private var scanningKey = false

    /** The key tab's chosen question set, kept across tab switches. */
    var keySet: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        tab = savedInstanceState?.getString("tab")?.let { n -> Tab.entries.firstOrNull { it.name == n } } ?: Tab.HOME
        scanningKey = savedInstanceState?.getBoolean("scanningKey") ?: false

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BACKGROUND) }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.CARD)
            elevation = dp(10).toFloat()
            setPadding(dp(6), dp(8), dp(6), dp(10))
        }
        root.addView(View(this).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        root.addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        if (!store.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tab.name)
        outState.putBoolean("scanningKey", scanningKey)
    }

    override fun onResume() {
        super.onResume()
        show(tab, animate = false)
    }

    fun show(t: Tab, animate: Boolean = true) {
        // Preserve scroll position when the same tab rebuilds (chip tap, choice change, etc.),
        // but reset to the top on tab switches so each tab opens where it was last laid out.
        val sameTab = t == tab && content.childCount > 0
        val savedY = if (sameTab) findScroll(content.getChildAt(0))?.scrollY ?: 0 else 0
        tab = t
        buildNav()
        val view = when (t) {
            Tab.HOME -> HomeTab(this).build()
            Tab.KEY -> KeyTab(this).build()
            Tab.SCANNER -> ScannerTab(this).build()
            Tab.RESULTS -> ResultsTab(this).build()
        }
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (sameTab && savedY > 0) view.post { findScroll(view)?.scrollTo(0, savedY) }
        if (animate) {
            view.alpha = 0f
            view.translationY = dp(8).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(160).start()
        }
    }

    /** Rebuilds the current tab (after a change made on it). */
    fun refresh() = show(tab, animate = false)

    private fun buildNav() {
        nav.removeAllViews()
        for (t in Tab.entries) {
            val on = t == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                background = ripple(rounded(Color.TRANSPARENT, dp(16).toFloat()), dp(16).toFloat())
                setOnClickListener { if (t != tab) show(t) else if (t == Tab.RESULTS && ResultsTab.openExam != null) { ResultsTab.openExam = null; refresh() } }
            }
            item.addView(ImageView(this).apply {
                setImageDrawable(IconDrawable(t.icon, if (on) Ui.ON_PRIMARY else Ui.MUTED, dp(22)))
                scaleType = ImageView.ScaleType.CENTER
                background = if (on) rounded(Ui.PRIMARY, dp(14).toFloat()) else null
            }, LinearLayout.LayoutParams(dp(60), dp(30)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(4) })
            item.addView(text(t.label, 12f, if (on) Ui.TEXT else Ui.MUTED, on).apply { gravity = Gravity.CENTER }, wrap().apply { gravity = Gravity.CENTER_HORIZONTAL })
            nav.addView(item, weighted())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            tab == Tab.RESULTS && ResultsTab.openExam != null -> { ResultsTab.openExam = null; refresh() }
            tab != Tab.HOME -> show(Tab.HOME)
            else -> super.onBackPressed()
        }
    }

    /** The ⋮ menu on every tab: app-level settings only. */
    fun appMenu() = bottomSheet("OMR Scanner") { col, d ->
        col.add(text("Appearance", TextStyle.LABEL, Ui.MUTED), block(8))
        val modes = listOf("dark", "light", "system")
        col.add(segmented(listOf("Dark", "Light", "Phone"), modes.indexOf(store.themeMode).coerceAtLeast(0)) { i ->
            if (modes[i] != store.themeMode) {
                store.themeMode = modes[i]
                d.dismiss()
                recreate()
            }
        }, block(16))
        col.add(listRow(Icon.SHARE, "Share app", "Send the download link to other teachers") { d.dismiss(); shareApp() }, block(0))
        col.add(listRow(Icon.SPARK, "Show introduction", null, Ui.ORANGE, Ui.ORANGE_BG) {
            d.dismiss(); startActivity(Intent(this, OnboardingActivity::class.java))
        }, block(0))
        col.add(listRow(Icon.INFO, "About", null, Ui.BLUE, Ui.PRIMARY_LIGHT) { d.dismiss(); about() }, block(0))
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "OMR Scanner")
            putExtra(Intent.EXTRA_TEXT, "OMR Scanner — check OMR answer sheets with your phone camera.\n$APK_URL")
        }
        startActivity(Intent.createChooser(send, "Share app"))
    }

    private fun about() = bottomSheet("About") { col, _ ->
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        col.add(text("OMR Scanner $version", TextStyle.HEADING), block(6))
        col.add(text("Reads OMR answer sheets from a photo or PDF and grades them on your phone. Works offline; nothing is uploaded.", TextStyle.BODY, Ui.MUTED), block(0))
    }

    // ---- Pictures ----------------------------------------------------------------------------

    fun scanSheet(camera: Boolean) {
        scanningKey = false
        if (camera) ImageSources.openCamera(this) else ImageSources.openGallery(this)
    }

    fun scanSheetPdf() { scanningKey = false; ImageSources.openPdf(this) }

    fun scanKey(camera: Boolean) {
        scanningKey = true
        if (camera) ImageSources.openCamera(this) else ImageSources.openGallery(this)
    }

    fun scanKeyPdf() { scanningKey = true; ImageSources.openPdf(this) }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = ImageSources.resultUri(this, requestCode, resultCode, data) ?: return
        // A PDF with several pages is a stack of sheets: check them all and make a report.
        if (!scanningKey && requestCode == ImageSources.REQUEST_PDF && ImageSources.pdfPageCount(this, uri) > 1) {
            startActivity(Intent(this, BatchActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            return
        }
        openScan(uri)
    }

    private fun openScan(uri: Uri) = startActivity(
        Intent(this, ScanActivity::class.java).setData(uri)
            .putExtra(ScanActivity.EXTRA_KEY_MODE, scanningKey)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )

    companion object {
        const val APK_URL = "https://github.com/zahid35pwd-creator/tgf/releases/download/omr-scanner-latest/OMR-Scanner.apk"
    }
}

/** 1.0 -> "1", 0.25 -> "0.25" (always ASCII digits, so the text can be parsed back). */
fun fmt(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
