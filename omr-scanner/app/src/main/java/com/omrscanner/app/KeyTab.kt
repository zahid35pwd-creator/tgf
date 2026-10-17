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
        val (root, column) = tabPage("Teacher's answer sheet", iconButton(Icon.MORE) { appMenu() })
        column.add(HomeTab.selectedSheet(this), block(14))
        if (!store.hasTemplate) {
            column.add(emptyState(Icon.KEY, "Select an OMR sheet first", "The answer key belongs to an OMR sheet. Choose the sheet on the Home tab, then come back here.",
                "Go to Home") { show(MainActivity.Tab.HOME) }, block(0))
            return root
        }

        setChips()?.let { column.add(it, block(14)) }

        val top = card(18)
        status = text("", TextStyle.HEADING)
        top.add(status, block(10))
        bar = progressBar(settings.questionCount, 0)
        top.add(bar, block(12))
        top.add(text("Scan a sheet filled in with the right answers, or tap them below. Questions without a key are not graded.", TextStyle.CAPTION, Ui.MUTED), block(14))
        top.add(button("Scan key with camera", ButtonStyle.PRIMARY, Icon.CAMERA) { scanKey(true) }, block(10))
        val more = horizontal()
        more.add(button("Photo", ButtonStyle.SECONDARY, Icon.GALLERY) { scanKey(false) }, weighted().apply { marginEnd = dp(8) })
        more.add(button("PDF", ButtonStyle.SECONDARY, Icon.PDF) { scanKeyPdf() }, weighted().apply { marginEnd = dp(8) })
        more.add(button("Type", ButtonStyle.SECONDARY, Icon.EDIT) { paste() }, weighted())
        top.add(more, block(0))
        column.add(top, block(12))

        // Marking used when grading against this key.
        val scheme = horizontal().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val r = dp(14).toFloat()
            background = ripple(rounded(Ui.CARD, r, Ui.DIVIDER, dp(1)), r)
            setOnClickListener { editMarks() }
        }
        val label = vertical()
        label.add(text("Marking", 14f, Ui.TEXT, true), wrap())
        label.add(text("Tap to change", 12f, Ui.PRIMARY), wrap())
        scheme.add(label, weighted())
        scheme.add(chip("Right +${fmt(settings.perCorrect)}", Ui.GREEN, Ui.GREEN_BG), wrap().apply { marginEnd = dp(6) })
        scheme.add(chip("Wrong ${if (settings.perWrong > 0) "−" + fmt(settings.perWrong) else "0"}", Ui.RED, Ui.RED_BG), wrap())
        column.add(scheme, block(18))

        val header = horizontal()
        header.add(sectionHeader("Questions"), weighted())
        header.add(text("Clear", 13.5f, Ui.RED, true).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { clear() }
        }, wrap())
        column.add(header, block(4))
        val grid = card(10)
        for (q in 0 until settings.questionCount) grid.add(questionRow(q), block(0))
        column.add(grid, block(0))
        updateStatus()
        root
    }

    private fun setChips(): View? = with(a) {
        val t = settings.template
        val field = t.field(t.setField)
        if (field == null) {
            // No Set field yet. For user-added sheets, let them pick a short letter field as the Set.
            if (!t.custom) return null
            val candidates = t.fields.filter { it.labels.size in 2..6 }
            if (candidates.isEmpty()) return null
            val link = text("＋ Add a Set field (ক খ গ ঘ, A B C D, …)", 13.5f, Ui.PRIMARY, true).apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val r = dp(14).toFloat()
                background = ripple(rounded(Ui.CARD, r, Ui.DIVIDER, dp(1)), r)
                setOnClickListener { pickSetField(candidates) }
            }
            return link
        }
        val box = vertical()
        box.add(text("Question set — enter a separate key for each set, or one key for all", 13f, Ui.MUTED).apply { setPadding(dp(4), 0, 0, dp(8)) }, wrap())
        val row = horizontal()
        val choices = listOf<Int?>(null) + field.labels.indices
        val withKeys = store.setsWithKeys()
        for (v in choices) {
            val label = if (v == null) "All sets" else "Set ${settings.setName(v)}" + if (v in withKeys) " ✓" else ""
            row.add(choiceChip(label, v == keySet) { keySet = v; refresh() }, wrap().apply { marginEnd = dp(8) })
        }
        box.add(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, block(0))
        if (t.custom) {
            val remove = text("Remove Set field", 12.5f, Ui.MUTED).apply {
                setPadding(dp(4), dp(10), dp(4), 0)
                setOnClickListener { saveSetField(null) }
            }
            box.add(remove, wrap())
        }
        box
    }

    private fun pickSetField(candidates: List<com.omrscanner.core.DigitField>) = a.bottomSheet("Pick the Set field") { col, d ->
        with(a) {
            col.add(text("Choose which short letter field on your sheet is the question set code (e.g. Set ক/খ/গ/ঘ).", TextStyle.CAPTION, Ui.MUTED), block(12))
            for (f in candidates) {
                col.add(listRow(com.omrscanner.app.ui.Icon.KEY, f.name, f.labels.joinToString(" · ")) {
                    d.dismiss()
                    saveSetField(f.name)
                }, block(0))
            }
        }
    }

    private fun saveSetField(name: String?) {
        val t = settings.template
        val updated = t.withSetField(name)
        a.store.addTemplate(updated)
        a.store.settings = a.store.settings.copy(template = updated)
        a.keySet = null
        a.refresh()
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
        val which = if (settings.template.setField != null) "${if (a.keySet == null) "All sets" else "Set " + settings.setName(a.keySet)} · " else ""
        status.text = "$which$n of ${settings.questionCount} set"
        bar.progress = n
        bar.progressTintList = android.content.res.ColorStateList.valueOf(if (n == settings.questionCount) Ui.GREEN else Ui.PRIMARY)
    }

    /** Marks for a right answer and the negative mark for a wrong one (defaults: 1 and 0). */
    private fun editMarks() = a.bottomSheet("Marking") { col, d ->
        with(a) {
            col.add(text("Blank answers always get 0. A wrong answer, or several bubbles filled, loses the negative mark.", TextStyle.CAPTION, Ui.MUTED), block(12))
            val decimal = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val (cBox, correct) = inputField("Marks for a right answer", fmt(settings.perCorrect), decimal)
            col.add(cBox, block(12))
            val (wBox, wrong) = inputField("Negative marks for a wrong answer (0 = none)", fmt(settings.perWrong), decimal)
            col.add(wBox, block(10))
            val presets = horizontal()
            for ((label, pc, pw) in listOf(Triple("1 / 0", 1.0, 0.0), Triple("1 / −0.25", 1.0, 0.25), Triple("1 / −0.5", 1.0, 0.5), Triple("4 / −1", 4.0, 1.0))) {
                presets.add(choiceChip(label, false) { correct.setText(fmt(pc)); wrong.setText(fmt(pw)) }, wrap().apply { marginEnd = dp(6) })
            }
            col.add(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(presets) }, block(16))
            col.add(button("Save") {
                val pc = correct.text.toString().toDoubleOrNull()
                val pw = wrong.text.toString().ifBlank { "0" }.toDoubleOrNull()
                when {
                    pc == null || pc <= 0 -> correct.error = "Enter a positive number"
                    pw == null || pw < 0 -> wrong.error = "Enter 0 or more"
                    else -> { d.dismiss(); store.settings = store.settings.copy(perCorrect = pc, perWrong = pw); refresh() }
                }
            }, block(0))
        }
    }

    private fun clear() = a.confirm("Clear the answer key?", "All ${settings.questionCount} answers of this key will be removed.", "Clear", danger = true) {
        val old = key.toList()
        key = MutableList(settings.questionCount) { null }
        a.store.saveKey(a.keySet, key)
        a.refresh()
        a.snackbar("Answer key cleared", "Undo") { a.store.saveKey(a.keySet, old); a.refresh() }
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
