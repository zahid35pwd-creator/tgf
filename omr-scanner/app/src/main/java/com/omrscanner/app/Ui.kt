package com.omrscanner.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Small helpers for building screens in code (the app has no layout XML). */
object Ui {
    const val PRIMARY = 0xFF3949AB.toInt()
    const val PRIMARY_LIGHT = 0xFFE8EAF6.toInt()
    const val BACKGROUND = 0xFFF3F4F8.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFF1B1D28.toInt()
    const val MUTED = 0xFF666B7A.toInt()
    const val DIVIDER = 0xFFE2E4EC.toInt()
    const val GREEN = 0xFF2E7D32.toInt()
    const val RED = 0xFFC62828.toInt()
    const val ORANGE = 0xFFEF6C00.toInt()
    const val PURPLE = 0xFF7B1FA2.toInt()
    const val BLUE = 0xFF1565C0.toInt()
}

fun Context.dp(v: Number): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun rounded(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radius
    if (strokeWidth > 0) setStroke(strokeWidth, stroke)
}

fun Context.text(s: CharSequence, sizeSp: Float = 15f, color: Int = Ui.TEXT, bold: Boolean = false) = TextView(this).apply {
    text = s
    textSize = sizeSp
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }

fun Context.button(label: String, style: ButtonStyle = ButtonStyle.PRIMARY, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 16f
    typeface = Typeface.DEFAULT_BOLD
    stateListAnimator = null
    minHeight = dp(52)
    setPadding(dp(16), dp(10), dp(16), dp(10))
    val (fill, fg, stroke) = when (style) {
        ButtonStyle.PRIMARY -> Triple(Ui.PRIMARY, 0xFFFFFFFF.toInt(), 0)
        ButtonStyle.SECONDARY -> Triple(Ui.CARD, Ui.PRIMARY, Ui.PRIMARY)
        ButtonStyle.DANGER -> Triple(Ui.CARD, Ui.RED, Ui.RED)
    }
    setTextColor(fg)
    background = RippleDrawable(
        ColorStateList.valueOf(0x33000000),
        rounded(fill, dp(12).toFloat(), stroke, if (stroke != 0) dp(1.5f) else 0),
        null,
    )
    setOnClickListener { onClick() }
}

fun Context.vertical(paddingDp: Int = 0) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
}

fun Context.horizontal() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card() = vertical(16).apply {
    background = rounded(Ui.CARD, dp(16).toFloat())
    elevation = dp(1).toFloat()
}

/** Full-width child with a bottom margin. */
fun Context.block(bottomDp: Int = 12) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
    bottomMargin = dp(bottomDp)
}

fun weighted(weight: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

fun LinearLayout.add(view: View, params: ViewGroup.LayoutParams = context.block()): View {
    addView(view, params)
    return view
}

/** Sets a scrolling, padded column as the activity's content and returns it. */
fun Activity.scrollingPage(): LinearLayout {
    val column = vertical(16)
    val scroll = ScrollView(this).apply {
        setBackgroundColor(Ui.BACKGROUND)
        isFillViewport = true
        addView(column)
    }
    setContentView(scroll)
    return column
}
