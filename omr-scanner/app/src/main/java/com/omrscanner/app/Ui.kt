package com.omrscanner.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable

/**
 * Design tokens. Colours have a light and a dark value and are chosen when a screen is created
 * ([apply]), following the phone's dark-mode setting.
 */
object Ui {
    var dark = false
        private set

    // Brand
    var PRIMARY = 0; private set
    var ON_PRIMARY = 0; private set
    var PRIMARY_LIGHT = 0; private set // tonal container
    var ON_PRIMARY_LIGHT = 0; private set

    // Surfaces
    var BACKGROUND = 0; private set
    var CARD = 0; private set
    var CARD_ALT = 0; private set // inset areas, alternating rows
    var TEXT = 0; private set
    var MUTED = 0; private set
    var DIVIDER = 0; private set

    // Meaning (also used to mark the sheet image)
    var GREEN = 0; private set
    var RED = 0; private set
    var ORANGE = 0; private set
    var PURPLE = 0; private set
    var BLUE = 0; private set
    var GREEN_BG = 0; private set
    var RED_BG = 0; private set
    var ORANGE_BG = 0; private set
    var PURPLE_BG = 0; private set

    fun apply(context: Context) {
        dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        fun c(light: Long, night: Long) = (if (dark) night else light).toInt()
        PRIMARY = c(0xFF4338CA, 0xFF8B8CF8)
        ON_PRIMARY = c(0xFFFFFFFF, 0xFF13123A)
        PRIMARY_LIGHT = c(0xFFE9E8FD, 0xFF2A2A5A)
        ON_PRIMARY_LIGHT = c(0xFF3730A3, 0xFFD9D9FF)
        BACKGROUND = c(0xFFF6F6FB, 0xFF0F1020)
        CARD = c(0xFFFFFFFF, 0xFF1A1B2E)
        CARD_ALT = c(0xFFF2F2F8, 0xFF22233A)
        TEXT = c(0xFF16172B, 0xFFEDEDF7)
        MUTED = c(0xFF6B6D85, 0xFF9C9EB8)
        DIVIDER = c(0xFFE6E6F0, 0xFF2C2D45)
        GREEN = c(0xFF16A34A, 0xFF4ADE80)
        RED = c(0xFFDC2626, 0xFFF87171)
        ORANGE = c(0xFFEA7C0C, 0xFFFBBF24)
        PURPLE = c(0xFF9333EA, 0xFFC084FC)
        BLUE = c(0xFF2563EB, 0xFF60A5FA)
        GREEN_BG = c(0xFFE8F7EE, 0xFF15301F)
        RED_BG = c(0xFFFDECEC, 0xFF3A1A1E)
        ORANGE_BG = c(0xFFFFF3E3, 0xFF372A12)
        PURPLE_BG = c(0xFFF4E9FE, 0xFF2D1D3E)
    }

    /** Colour for a percentage: green ≥ 80, primary ≥ 60, orange ≥ 40, red below. */
    fun bandColor(percent: Double) = when {
        percent >= 80 -> GREEN
        percent >= 60 -> PRIMARY
        percent >= 40 -> ORANGE
        else -> RED
    }
}

/** Screens share this: theme colours, status/navigation bar styling and screen transitions. */
abstract class BaseActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        Ui.apply(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BACKGROUND
        window.navigationBarColor = Ui.CARD
        var flags = 0
        if (!Ui.dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }

    override fun startActivity(intent: android.content.Intent?) {
        super.startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

fun Context.dp(v: Number): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun rounded(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radius
    if (strokeWidth > 0) setStroke(strokeWidth, stroke)
}

/** Ripple over [content], clipped to the same rounded shape. */
fun ripple(content: Drawable, radius: Float, color: Int = 0x22000000): Drawable =
    RippleDrawable(ColorStateList.valueOf(if (Ui.dark) 0x33FFFFFF else color), content, rounded(Color.WHITE, radius))

// ---- Text -------------------------------------------------------------------------------------

enum class TextStyle(val sp: Float, val bold: Boolean) {
    DISPLAY(32f, true), TITLE(22f, true), HEADING(17f, true), BODY(15f, false), LABEL(14f, true), CAPTION(13f, false), OVERLINE(12f, true)
}

fun Context.text(s: CharSequence, sizeSp: Float = 15f, color: Int = Ui.TEXT, bold: Boolean = false) = TextView(this).apply {
    text = s
    textSize = sizeSp
    setTextColor(color)
    typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
    setLineSpacing(0f, 1.12f)
}

fun Context.text(s: CharSequence, style: TextStyle, color: Int = Ui.TEXT) = text(s, style.sp, color, style.bold).apply {
    if (style == TextStyle.OVERLINE) { isAllCaps = true; letterSpacing = 0.08f }
    if (style == TextStyle.DISPLAY || style == TextStyle.TITLE) letterSpacing = -0.01f
}

// ---- Buttons ----------------------------------------------------------------------------------

enum class ButtonStyle { PRIMARY, SECONDARY, OUTLINE, DANGER, TEXT }

fun Context.button(label: String, style: ButtonStyle = ButtonStyle.PRIMARY, icon: Icon? = null, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 15f
    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    stateListAnimator = null
    minHeight = dp(52)
    minimumHeight = dp(52)
    setPadding(dp(18), dp(10), dp(18), dp(10))
    val radius = dp(14).toFloat()
    val (fill, fg, stroke) = when (style) {
        ButtonStyle.PRIMARY -> Triple(Ui.PRIMARY, Ui.ON_PRIMARY, 0)
        ButtonStyle.SECONDARY -> Triple(Ui.PRIMARY_LIGHT, Ui.ON_PRIMARY_LIGHT, 0)
        ButtonStyle.OUTLINE -> Triple(Color.TRANSPARENT, Ui.PRIMARY, Ui.DIVIDER)
        ButtonStyle.DANGER -> Triple(Ui.RED_BG, Ui.RED, 0)
        ButtonStyle.TEXT -> Triple(Color.TRANSPARENT, Ui.PRIMARY, 0)
    }
    setTextColor(fg)
    background = ripple(rounded(fill, radius, stroke, if (stroke != 0) dp(1.5f) else 0), radius)
    if (icon != null) {
        val d = IconDrawable(icon, fg, dp(20))
        setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
        compoundDrawablePadding = dp(10)
    }
    gravity = Gravity.CENTER
    setOnClickListener { onClick() }
}

/** A round icon-only button. */
fun Context.iconButton(icon: Icon, tint: Int = Ui.TEXT, fill: Int = Color.TRANSPARENT, sizeDp: Int = 44, onClick: () -> Unit) = ImageView(this).apply {
    setImageDrawable(IconDrawable(icon, tint, dp(22)))
    scaleType = ImageView.ScaleType.CENTER
    val r = dp(sizeDp / 2).toFloat()
    background = ripple(rounded(fill, r), r)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    isClickable = true
    setOnClickListener { onClick() }
}

// ---- Layout -----------------------------------------------------------------------------------

fun Context.vertical(paddingDp: Int = 0) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
}

fun Context.horizontal() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card(paddingDp: Int = 18) = vertical(paddingDp).apply {
    background = rounded(Ui.CARD, dp(20).toFloat(), if (Ui.dark) Ui.DIVIDER else 0, if (Ui.dark) dp(1) else 0)
    if (!Ui.dark) elevation = dp(1.5f).toFloat()
}

/** A tappable card. */
fun Context.clickableCard(paddingDp: Int = 18, onClick: () -> Unit) = card(paddingDp).apply {
    val r = dp(20).toFloat()
    background = ripple(rounded(Ui.CARD, r, if (Ui.dark) Ui.DIVIDER else 0, if (Ui.dark) dp(1) else 0), r)
    isClickable = true
    setOnClickListener { onClick() }
}

/** Full-width child with a bottom margin. */
fun Context.block(bottomDp: Int = 12) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
    bottomMargin = dp(bottomDp)
}

fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

fun weighted(weight: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

fun LinearLayout.add(view: View, params: ViewGroup.LayoutParams = context.block()): View {
    addView(view, params)
    return view
}

fun Context.spacer(heightDp: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }

/** A small uppercase section title. */
fun Context.sectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) = horizontal().apply {
    setPadding(dp(4), dp(8), dp(4), dp(6))
    add(text(title, TextStyle.OVERLINE, Ui.MUTED), weighted())
    if (action != null && onAction != null) add(text(action, TextStyle.LABEL, Ui.PRIMARY).apply {
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = ripple(rounded(Color.TRANSPARENT, dp(8).toFloat()), dp(8).toFloat())
        setOnClickListener { onAction() }
    }, wrap())
}

/** A pill-shaped label. */
fun Context.chip(label: String, fg: Int = Ui.ON_PRIMARY_LIGHT, bg: Int = Ui.PRIMARY_LIGHT, icon: Icon? = null) = text(label, 13f, fg, true).apply {
    setPadding(dp(12), dp(6), dp(12), dp(6))
    background = rounded(bg, dp(50).toFloat())
    gravity = Gravity.CENTER_VERTICAL
    if (icon != null) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(IconDrawable(icon, fg, dp(16)), null, null, null)
        compoundDrawablePadding = dp(6)
    }
}

/** A selectable pill (filter / choice chip). */
fun Context.choiceChip(label: String, selected: Boolean, onClick: () -> Unit) = text(label, 14f, if (selected) Ui.ON_PRIMARY else Ui.TEXT, true).apply {
    setPadding(dp(16), dp(8), dp(16), dp(8))
    val r = dp(50).toFloat()
    background = ripple(rounded(if (selected) Ui.PRIMARY else Ui.CARD, r, if (selected) 0 else Ui.DIVIDER, if (selected) 0 else dp(1)), r)
    gravity = Gravity.CENTER
    setOnClickListener { onClick() }
}

/** Two to four options in one rounded bar; [onSelect] gets the index. */
fun Context.segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
    val bar = horizontal().apply {
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = rounded(Ui.CARD_ALT, dp(14).toFloat())
    }
    options.forEachIndexed { i, o ->
        val on = i == selected
        bar.add(text(o, 14f, if (on) Ui.TEXT else Ui.MUTED, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            val r = dp(11).toFloat()
            background = ripple(rounded(if (on) Ui.CARD else Color.TRANSPARENT, r), r)
            if (on && !Ui.dark) elevation = dp(1).toFloat()
            setOnClickListener { onSelect(i) }
        }, weighted())
    }
    return bar
}

/** A number with a label, on a tinted tile. */
fun Context.statTile(value: String, label: String, fg: Int, bg: Int) = vertical().apply {
    setPadding(dp(10), dp(12), dp(10), dp(12))
    gravity = Gravity.CENTER_HORIZONTAL
    background = rounded(bg, dp(16).toFloat())
    add(text(value, 22f, fg, true), wrap())
    add(text(label, 12f, fg, false).apply { alpha = 0.85f }, wrap())
}

/** A list row: leading icon in a tinted circle, title, optional subtitle, optional trailing view. */
fun Context.listRow(icon: Icon, title: String, subtitle: String? = null, tint: Int = Ui.PRIMARY, tintBg: Int = Ui.PRIMARY_LIGHT,
                    trailing: View? = null, chevron: Boolean = true, onClick: (() -> Unit)? = null): LinearLayout {
    val row = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
    row.add(ImageView(this).apply {
        setImageDrawable(IconDrawable(icon, tint, dp(20)))
        scaleType = ImageView.ScaleType.CENTER
        background = rounded(tintBg, dp(12).toFloat())
    }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
    val texts = vertical()
    texts.add(text(title, 15f, Ui.TEXT, true), wrap())
    if (subtitle != null) texts.add(text(subtitle, 13f, Ui.MUTED), wrap())
    row.add(texts, weighted())
    if (trailing != null) row.add(trailing, wrap())
    if (chevron && onClick != null) row.add(ImageView(this).apply { setImageDrawable(IconDrawable(Icon.CHEVRON, Ui.MUTED, dp(18))) }, wrap())
    if (onClick != null) {
        row.background = ripple(rounded(Color.TRANSPARENT, dp(16).toFloat()), dp(16).toFloat())
        row.setOnClickListener { onClick() }
    }
    return row
}

fun Context.divider() = View(this).apply {
    setBackgroundColor(Ui.DIVIDER)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(68) }
}

/** An empty-state block: big soft icon, title, text and an optional action. */
fun Context.emptyState(icon: Icon, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) = vertical(24).apply {
    gravity = Gravity.CENTER_HORIZONTAL
    add(ImageView(context).apply {
        setImageDrawable(IconDrawable(icon, Ui.PRIMARY, dp(40)))
        scaleType = ImageView.ScaleType.CENTER
        background = rounded(Ui.PRIMARY_LIGHT, dp(44).toFloat())
    }, LinearLayout.LayoutParams(dp(88), dp(88)).apply { bottomMargin = dp(16) })
    add(text(title, TextStyle.HEADING).apply { gravity = Gravity.CENTER }, wrap())
    add(text(body, 14f, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(16)) }, wrap())
    if (action != null && onAction != null) add(button(action, ButtonStyle.SECONDARY, onClick = onAction), wrap())
}

// ---- Screens ----------------------------------------------------------------------------------

/** A top app bar: optional back arrow, title, optional trailing actions. */
fun Activity.appBar(title: String, back: Boolean = true, vararg actions: View): LinearLayout {
    val bar = horizontal().apply { setPadding(dp(8), dp(10), dp(8), dp(6)) }
    if (back) bar.add(iconButton(Icon.BACK) { onBackPressed() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(4) })
    else bar.add(spacer(1), LinearLayout.LayoutParams(dp(10), dp(1)))
    bar.add(text(title, TextStyle.TITLE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, weighted())
    for (a in actions) bar.add(a, wrap())
    return bar
}

/**
 * Sets a screen with an app bar and a scrolling, padded column, and returns the column. When [footer]
 * is given it stays pinned at the bottom (e.g. the main actions).
 */
fun Activity.scrollingPage(title: String? = null, back: Boolean = true, footer: View? = null, vararg actions: View): LinearLayout {
    val root = vertical().apply { setBackgroundColor(Ui.BACKGROUND) }
    if (title != null) root.add(appBar(title, back, *actions), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val column = vertical().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(column)
    }
    root.add(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    if (footer != null) root.add(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    setContentView(root)
    return column
}

/** A footer bar holding buttons side by side, for [scrollingPage]. */
fun Context.footerBar(vararg buttons: View) = horizontal().apply {
    setPadding(dp(16), dp(12), dp(16), dp(14))
    setBackgroundColor(Ui.CARD)
    elevation = dp(8).toFloat()
    buttons.forEachIndexed { i, b -> add(b, weighted().apply { if (i > 0) marginStart = dp(10) }) }
}

/** A centred full-screen message with a spinner, for work in progress. */
fun Activity.busyScreen(title: String, subtitle: String = ""): TextView {
    val box = vertical(32).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.BACKGROUND) }
    box.add(android.widget.ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(Ui.PRIMARY) },
        LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(20) })
    box.add(text(title, TextStyle.HEADING).apply { gravity = Gravity.CENTER }, wrap())
    val sub = text(subtitle, 14f, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
    box.add(sub, wrap())
    setContentView(box)
    return sub
}

/** A bottom sheet dialog with rounded top corners; [build] fills its column. */
fun Activity.bottomSheet(title: String?, build: (LinearLayout, android.app.Dialog) -> Unit): android.app.Dialog {
    val dialog = android.app.Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    val column = vertical().apply {
        setPadding(dp(20), dp(10), dp(20), dp(24))
        background = GradientDrawable().apply {
            setColor(Ui.CARD)
            val r = dp(26).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }
    column.add(View(this).apply { background = rounded(Ui.DIVIDER, dp(3).toFloat()) },
        LinearLayout.LayoutParams(dp(40), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })
    if (title != null) column.add(text(title, TextStyle.HEADING), block(12))
    build(column, dialog)
    val scroll = ScrollView(this).apply { addView(column) }
    dialog.setContentView(scroll)
    dialog.window?.apply {
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM)
        setWindowAnimations(android.R.style.Animation_InputMethod)
        setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    dialog.show()
    return dialog
}

/** Simple confirm sheet. */
fun Activity.confirm(title: String, body: String, action: String, danger: Boolean = false, onConfirm: () -> Unit) {
    bottomSheet(title) { col, d ->
        col.add(text(body, 15f, Ui.MUTED), block(18))
        val row = horizontal()
        row.add(button("Cancel", ButtonStyle.OUTLINE) { d.dismiss() }, weighted().apply { marginEnd = dp(10) })
        row.add(button(action, if (danger) ButtonStyle.DANGER else ButtonStyle.PRIMARY) { d.dismiss(); onConfirm() }, weighted())
        col.add(row, block(0))
    }
}

/** Puts [view] in a frame (for overlays such as floating buttons). */
fun Context.frame(view: View) = FrameLayout(this).apply { addView(view) }

/** A titled scrolling column for a tab inside [MainActivity]; returns (whole view, column). */
fun Activity.tabPage(title: String, vararg actions: View): Pair<View, LinearLayout> {
    val root = vertical().apply { setBackgroundColor(Ui.BACKGROUND) }
    val bar = horizontal().apply { setPadding(dp(20), dp(14), dp(10), dp(4)) }
    bar.add(text(title, TextStyle.TITLE.sp + 4, Ui.TEXT, true).apply { letterSpacing = -0.01f }, weighted())
    for (a in actions) bar.add(a, wrap())
    root.add(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val column = vertical().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
    root.add(ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(column)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    return root to column
}

/** A filled rounded text field with a small label above it; returns (block, field). */
fun Context.inputField(label: String, value: String, inputType: Int, hint: String = ""): Pair<LinearLayout, android.widget.EditText> {
    val box = vertical()
    box.add(text(label, TextStyle.CAPTION, Ui.MUTED).apply { setPadding(dp(4), 0, 0, dp(6)) }, wrap())
    val edit = android.widget.EditText(this).apply {
        setText(value)
        this.inputType = inputType
        this.hint = hint
        setSingleLine(inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        textSize = 16f
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.MUTED)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Ui.CARD_ALT, dp(14).toFloat(), Ui.DIVIDER, dp(1))
        setSelection(text.length)
    }
    box.add(edit, block(0))
    return box to edit
}

/** A thin rounded linear progress bar. */
fun Context.progressBar(max: Int, value: Int, color: Int = Ui.PRIMARY) =
    android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = false
        this.max = max.coerceAtLeast(1)
        progress = value
        progressTintList = ColorStateList.valueOf(color)
        progressBackgroundTintList = ColorStateList.valueOf(Ui.CARD_ALT)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        scaleY = 1.6f
    }

/** A small dark message at the bottom of the screen with an optional action (e.g. Undo). */
fun Activity.snackbar(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val content = findViewById<FrameLayout>(android.R.id.content) ?: return
    val bar = horizontal().apply {
        setPadding(dp(18), dp(12), dp(10), dp(12))
        background = rounded(if (Ui.dark) 0xFFE9E9F5.toInt() else 0xFF22233A.toInt(), dp(14).toFloat())
        elevation = dp(6).toFloat()
    }
    val fg = if (Ui.dark) 0xFF16172B.toInt() else Color.WHITE
    bar.add(text(message, 14f, fg), weighted())
    val remove = Runnable { content.removeView(bar) }
    if (action != null && onAction != null) {
        bar.add(text(action, 14f, if (Ui.dark) 0xFF4338CA.toInt() else 0xFFA5A3F5.toInt(), true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { content.removeView(bar); onAction() }
        }, wrap())
    }
    content.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        setMargins(dp(16), 0, dp(16), dp(96))
    })
    bar.alpha = 0f
    bar.translationY = dp(20).toFloat()
    bar.animate().alpha(1f).translationY(0f).setDuration(180).start()
    bar.postDelayed(remove, 4000)
}

/** A round tinted icon badge. */
fun Context.iconBadge(icon: Icon, tint: Int, bg: Int, sizeDp: Int = 44, iconDp: Int = 22) = ImageView(this).apply {
    setImageDrawable(IconDrawable(icon, tint, dp(iconDp)))
    scaleType = ImageView.ScaleType.CENTER
    background = rounded(bg, dp(sizeDp * 0.32f).toFloat())
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun pct(v: Double) = String.format(java.util.Locale.US, "%.0f%%", v)

/** The step list of a [processingScreen]; [reach] ticks off every step before [index]. */
class ProcessingSteps(private val rows: List<Pair<ImageView, TextView>>, private val root: View) {
    private var current = -1

    fun reach(index: Int) {
        if (index <= current) return
        current = index
        val c = root.context
        rows.forEachIndexed { i, (icon, label) ->
            when {
                i < index -> {
                    icon.setImageDrawable(IconDrawable(Icon.CHECK, Ui.ON_PRIMARY, c.dp(14)))
                    icon.background = rounded(Ui.GREEN, c.dp(12).toFloat())
                    label.setTextColor(Ui.TEXT)
                }
                i == index -> {
                    icon.setImageDrawable(null)
                    icon.background = rounded(Ui.PRIMARY_LIGHT, c.dp(12).toFloat(), Ui.PRIMARY, c.dp(2))
                    label.setTextColor(Ui.TEXT)
                }
                else -> {
                    icon.setImageDrawable(null)
                    icon.background = rounded(Ui.CARD_ALT, c.dp(12).toFloat())
                    label.setTextColor(Ui.MUTED)
                }
            }
        }
    }

    /** Moves through the steps on a timer (up to the last one) while the real work runs. */
    fun autoAdvance(everyMs: Long = 650) {
        val tick = object : Runnable {
            override fun run() {
                if (current < rows.size - 1 && root.isAttachedToWindow) { reach(current + 1); root.postDelayed(this, everyMs) }
            }
        }
        root.postDelayed(tick, everyMs)
    }
}

/** A full-screen "working on it" page: animated sheet, title and a list of steps. */
fun Activity.processingScreen(title: String, steps: List<String>, subtitle: String = ""): Pair<ProcessingSteps, TextView> {
    val box = vertical(28).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.BACKGROUND) }
    box.add(com.omrscanner.app.ui.ScanAnimationView(this, if (Ui.dark) 0xFF2A2B45.toInt() else Color.WHITE, Ui.TEXT, Ui.PRIMARY).apply {
        if (!Ui.dark) elevation = 0f
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply { bottomMargin = dp(20) })
    box.add(text(title, TextStyle.TITLE).apply { gravity = Gravity.CENTER }, wrap().apply { gravity = Gravity.CENTER_HORIZONTAL })
    val sub = text(subtitle, TextStyle.CAPTION, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
    box.add(sub, wrap().apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(22) })
    val list = card(18)
    val rows = steps.map { s ->
        val row = horizontal().apply { setPadding(0, dp(6), 0, dp(6)) }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER; background = rounded(Ui.CARD_ALT, dp(12).toFloat()) }
        row.add(icon, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) })
        val label = text(s, 15f, Ui.MUTED, true)
        row.add(label, weighted())
        list.add(row, block(0))
        icon to label
    }
    box.add(list, LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
    setContentView(box)
    val p = ProcessingSteps(rows, box)
    p.reach(0)
    return p to sub
}
