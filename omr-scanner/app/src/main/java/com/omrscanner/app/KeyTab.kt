package com.omrscanner.app

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable
import com.omrscanner.core.AnswerKeyCodec
import com.omrscanner.core.SheetSpec

/** Answer key editor: tap the right option per question, paste, or scan a filled-in key sheet. */
class KeyTab(private val a: MainActivity) {
    private val settings = a.store.settings
    private var key: MutableList<Int?> = a.store.storedKey(a.keySet).toMutableList()
    private lateinit var status: TextView
    private lateinit var bar: android.widget.ProgressBar

    fun build(): View = with(a) {
        if (settings.template.let { it.field(it.setField) } == null) keySet = null
        val more = iconButton(Icon.MORE) { menu() }
        val (root, column) = tabPage("Answer key", more)

        setChips()?.let { column.add(it, block(12)) }

        val top = card(18)
        val line = horizontal()
        status = text("", TextStyle.HEADING)
        line.add(status, weighted())
        top.add(line, block(10))
        bar = progressBar(settings.questionCount, 0)
        top.add(bar, block(12))
        top.add(text("Tap the right option for each question. Questions without a key are not graded.", TextStyle.CAPTION, Ui.MUTED), block(14))
        val buttons = horizontal()
        buttons.add(button("Scan key sheet", ButtonStyle.PRIMARY, Icon.CAMERA) { scanKey(true) }, weighted().apply { marginEnd = dp(10) })
        buttons.add(button("Picture", ButtonStyle.SECONDARY, Icon.GALLERY) { scanKey(false) }, weighted())
        top.add(buttons, block(0))
        column.add(top, block(16))

        column.add(sectionHeader("Questions"), block(4))
        val grid = card(10)
        for (q in 0 until settings.questionCount) grid.add(questionRow(q), block(0))
        column.add(grid, block(0))
        updateStatus()
        root
    }

    private fun setChips(): View? = with(a) {
        val field = settings.template.let { it.field(it.setField) } ?: return null
        val row = horizontal()
        val choices = listOf<Int?>(null) + field.labels.indices
        val withKeys = store.setsWithKeys()
        for (v in choices) {
            val label = if (v == null) "All sets" else "Set ${settings.setName(v)}" + if (v in withKeys) " ✓" else ""
            row.add(choiceChip(label, v == keySet) { keySet = v; refresh() }, wrap().apply { marginEnd = dp(8) })
        }
        android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun questionRow(q: Int): View = with(a) {
        val row = horizontal().apply { setPadding(dp(6), dp(5), dp(6), dp(5)) }
        row.add(text("${q + 1}", 15f, Ui.MUTED, true).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(dp(34), dp(42)).apply { marginEnd = dp(14) })
        val bubbles = ArrayList<FrameLayout>()
        for (o in 0 until settings.optionCount) {
            val bubble = FrameLayout(this)
            bubbles.add(bubble)
            bubble.setOnClickListener {
                key[q] = if (key[q] == o) null else o
                store.saveKey(keySet, key)
                bubbles.forEachIndexed { i, b -> paint(b, i, key[q] == i) }
                updateStatus()
            }
            paint(bubble, o, key[q] == o)
            row.add(bubble, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        }
        row
    }

    private fun paint(b: FrameLayout, option: Int, on: Boolean) = with(a) {
        b.removeAllViews()
        val r = dp(21).toFloat()
        b.background = ripple(rounded(if (on) Ui.PRIMARY else Color.TRANSPARENT, r, if (on) 0 else Ui.DIVIDER, if (on) 0 else dp(1.5f)), r)
        val label = if (on) android.widget.ImageView(this).apply { setImageDrawable(IconDrawable(Icon.CHECK, Ui.ON_PRIMARY, dp(20))); scaleType = android.widget.ImageView.ScaleType.CENTER }
        else text(settings.label(option), 15f, Ui.MUTED, true).apply { gravity = Gravity.CENTER }
        b.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        b.contentDescription = settings.label(option)
    }

    private fun updateStatus() {
        val n = key.count { it != null }
        val which = if (settings.template.setField != null) "${settings.setName(a.keySet)} · " else ""
        status.text = "$which$n of ${settings.questionCount} set"
        bar.progress = n
        bar.progressTintList = android.content.res.ColorStateList.valueOf(if (n == settings.questionCount) Ui.GREEN else Ui.PRIMARY)
    }

    private fun menu() = a.bottomSheet("Answer key") { col, d ->
        col.add(a.listRow(Icon.EDIT, "Type or paste", "One letter per question, e.g. ABDCA…") { d.dismiss(); paste() }, a.block(0))
        col.add(a.listRow(Icon.TRASH, "Clear this key", null, Ui.RED, Ui.RED_BG) {
            d.dismiss()
            a.confirm("Clear the answer key?", "All ${settings.questionCount} answers of this key will be removed.", "Clear", danger = true) {
                val old = key.toList()
                key = MutableList(settings.questionCount) { null }
                a.store.saveKey(a.keySet, key)
                a.refresh()
                a.snackbar("Answer key cleared", "Undo") { a.store.saveKey(a.keySet, old); a.refresh() }
            }
        }, a.block(0))
    }

    private fun paste() = a.bottomSheet("Type or paste the key") { col, d ->
        col.add(a.text("One letter per question in order (A, B, C… or ক, খ, গ…). Use - to skip a question. Spaces and numbers are ignored.", TextStyle.CAPTION, Ui.MUTED), a.block(12))
        val (box, input) = a.inputField("Key", AnswerKeyCodec.encode(key), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, "ABDCA…")
        col.add(box, a.block(16))
        col.add(a.button("Save key") {
            // Accept Bangla option letters too (ক = A, খ = B, …).
            val cleaned = input.text.toString()
                .map { ch -> SheetSpec.BANGLA_LABELS.indexOf(ch.toString()).let { i -> if (i >= 0) 'A' + i else ch } }
                .filter { it.isLetter() || it == '-' }
                .joinToString("")
            val decoded = AnswerKeyCodec.decode(cleaned, settings.optionCount)
            a.store.saveKey(a.keySet, List(settings.questionCount) { decoded.getOrNull(it) })
            d.dismiss()
            a.refresh()
        }, a.block(0))
    }
}
