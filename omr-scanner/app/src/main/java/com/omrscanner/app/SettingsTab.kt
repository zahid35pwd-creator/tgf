package com.omrscanner.app

import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.IconDrawable
import com.omrscanner.core.SheetSpec
import com.omrscanner.core.SheetTemplate

/** Exam settings, sheet types, printing and about, as grouped rows. */
class SettingsTab(private val a: MainActivity) {
    private val s = a.store.settings

    private fun save(new: ExamSettings) {
        a.store.settings = new
        a.refresh()
    }

    fun build(): View = with(a) {
        val (root, column) = tabPage("Settings")

        column.add(sectionHeader("Exam"), block(4))
        val exam = card(6)
        exam.add(valueRow(Icon.EDIT, "Exam name", s.examName.ifBlank { "Not set" }) {
            textSheet("Exam name", "Printed on the sheet and the reports", s.examName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) { v ->
                save(s.copy(examName = v.trim())); null
            }
        }, block(0))
        exam.add(divider(), dividerParams())
        exam.add(valueRow(Icon.SHEET, "Questions", "${s.questionCount}", stepper(s.questionCount, 1, s.template.maxQuestions) { save(s.copy(questionCount = it)) }) {
            textSheet("Number of questions", "1 to ${s.template.maxQuestions} on this sheet", s.questionCount.toString(), InputType.TYPE_CLASS_NUMBER) { v ->
                val n = v.toIntOrNull()
                if (n == null || n !in 1..s.template.maxQuestions) "Enter 1 to ${s.template.maxQuestions}" else { save(s.copy(questionCount = n)); null }
            }
        }, block(0))
        exam.add(divider(), dividerParams())
        val optionChoices = listOf(2, 3, 4, 5).filter { it <= s.template.maxOptions }.takeLast(3)
        exam.add(segmentRow(Icon.CHECK, "Options", optionChoices.map { "$it" }, optionChoices.indexOf(s.optionCount).coerceAtLeast(0), 50) { i ->
            save(s.copy(optionCount = optionChoices[i]))
        }, block(0))
        exam.add(divider(), dividerParams())
        exam.add(segmentRow(Icon.INFO, "Option letters", listOf("ABC", "কখগ"), if (s.banglaLabels) 1 else 0, 64) { i ->
            save(s.copy(banglaLabels = i == 1))
        }, block(0))
        exam.add(divider(), dividerParams())
        exam.add(valueRow(Icon.PLUS, "Marks per correct", fmt(s.perCorrect), tint = Ui.GREEN, tintBg = Ui.GREEN_BG) {
            textSheet("Marks per correct answer", "For example 1 or 2.5", fmt(s.perCorrect), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { v ->
                val x = v.toDoubleOrNull()
                if (x == null || x <= 0) "Enter a positive number" else { save(s.copy(perCorrect = x)); null }
            }
        }, block(0))
        exam.add(divider(), dividerParams())
        exam.add(valueRow(Icon.CROSS, "Negative marks per wrong", if (s.perWrong > 0) "−${fmt(s.perWrong)}" else "None", tint = Ui.RED, tintBg = Ui.RED_BG) {
            textSheet("Negative marks per wrong answer", "0 for none, e.g. 0.25 or 0.5", fmt(s.perWrong), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { v ->
                val x = v.ifBlank { "0" }.toDoubleOrNull()
                if (x == null || x < 0) "Enter 0 or more" else { save(s.copy(perWrong = x)); null }
            }
        }, block(0))
        column.add(exam, block(18))

        column.add(sectionHeader("Sheet type"), block(4))
        for (t in store.templates()) column.add(templateCard(t), block(10))
        val add = clickableCard(16) { startActivity(Intent(this, SheetSetupActivity::class.java)) }
        val addRow = horizontal()
        addRow.add(iconBadge(Icon.PLUS, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 40, 20), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        val addText = vertical()
        addText.add(text("Add a new sheet type", 15f, Ui.PRIMARY, true), wrap())
        addText.add(text("Teach the app any OMR sheet from a blank copy", 13f, Ui.MUTED), wrap())
        addRow.add(addText, weighted())
        add.add(addRow, block(0))
        add.background = ripple(rounded(Ui.CARD, dp(20).toFloat(), Ui.PRIMARY, dp(1.5f)), dp(20).toFloat())
        column.add(add, block(18))

        column.add(sectionHeader("Tools"), block(4))
        val tools = card(6)
        if (s.template.printable) {
            tools.add(listRow(Icon.PRINT, "Print blank sheet", "With your exam name and question count") { SheetPrinter.print(this, s.sheetSpec) }, block(0))
            tools.add(divider(), dividerParams())
        }
        tools.add(listRow(Icon.PDF, "Check a PDF of sheets", "Word report sorted by roll", Ui.PURPLE, Ui.PURPLE_BG) {
            startActivity(Intent(this, BatchActivity::class.java))
        }, block(0))
        tools.add(divider(), dividerParams())
        tools.add(listRow(Icon.SPARK, "Show the introduction", null, Ui.ORANGE, Ui.ORANGE_BG) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }, block(0))
        column.add(tools, block(18))

        column.add(sectionHeader("About"), block(4))
        val about = card(18)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        about.add(text("OMR Scanner $version", TextStyle.HEADING), block(4))
        about.add(text("Reads OMR answer sheets from a photo or PDF and grades them on your phone. Works offline; nothing is uploaded.", TextStyle.CAPTION, Ui.MUTED), block(0))
        column.add(about, block(0))
        root
    }

    private fun dividerParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1)).apply { marginStart = a.dp(68) }

    private fun valueRow(icon: Icon, title: String, value: String, trailing: View? = null, tint: Int = Ui.PRIMARY, tintBg: Int = Ui.PRIMARY_LIGHT, onClick: () -> Unit): View =
        a.listRow(icon, title, value, tint, tintBg, trailing, chevron = trailing == null, onClick = onClick)

    private fun segmentRow(icon: Icon, title: String, options: List<String>, selected: Int, optionDp: Int, onSelect: (Int) -> Unit): View = with(a) {
        val row = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        row.add(iconBadge(icon, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 40, 20), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        row.add(text(title, 15f, Ui.TEXT, true), weighted())
        row.add(segmented(options, selected) { if (it != selected) onSelect(it) }, LinearLayout.LayoutParams(dp(optionDp * options.size + 8), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        row
    }

    /** − value + buttons. */
    private fun stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit): View = with(a) {
        val box = horizontal().apply { background = rounded(Ui.CARD_ALT, dp(14).toFloat()) }
        fun step(label: String, delta: Int) = text(label, 20f, if ((value + delta) in min..max) Ui.PRIMARY else Ui.DIVIDER, true).apply {
            gravity = Gravity.CENTER
            val r = dp(14).toFloat()
            background = ripple(rounded(android.graphics.Color.TRANSPARENT, r), r)
            setOnClickListener { if ((value + delta) in min..max) onChange(value + delta) }
        }
        box.add(step("−", -1), LinearLayout.LayoutParams(dp(38), dp(38)))
        box.add(text("$value", 15f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; minWidth = dp(34) }, wrap())
        box.add(step("+", 1), LinearLayout.LayoutParams(dp(38), dp(38)))
        box
    }

    /** A bottom sheet with one text field; [onSave] returns an error message or null when saved. */
    private fun textSheet(title: String, hint: String, value: String, type: Int, onSave: (String) -> String?) = a.bottomSheet(title) { col, d ->
        val (box, edit) = a.inputField(hint, value, type)
        col.add(box, a.block(16))
        col.add(a.button("Save") {
            val err = onSave(edit.text.toString())
            if (err != null) edit.error = err else d.dismiss()
        }, a.block(0))
        edit.requestFocus()
    }

    private fun templateCard(t: SheetTemplate): View = with(a) {
        val on = t.id == s.templateId
        val r = dp(20).toFloat()
        val c = clickableCard(14) {
            if (on) return@clickableCard
            // Board sheets print ক খ গ ঘ; default to them when switching to one.
            save(s.copy(
                template = t,
                questionCount = t.maxQuestions,
                optionCount = s.optionCount.coerceAtMost(t.maxOptions),
                banglaLabels = t.optionLabels == SheetSpec.BANGLA_LABELS,
            ))
            snackbar("Now using \"${t.name}\"")
        }
        c.background = ripple(rounded(if (on) Ui.PRIMARY_LIGHT else Ui.CARD, r, if (on) Ui.PRIMARY else if (Ui.dark) Ui.DIVIDER else 0, if (on) dp(1.5f) else if (Ui.dark) dp(1) else 0), r)
        val row = horizontal()
        row.add(iconBadge(Icon.SHEET, if (on) Ui.ON_PRIMARY else Ui.PRIMARY, if (on) Ui.PRIMARY else Ui.PRIMARY_LIGHT, 44, 22),
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val texts = vertical()
        texts.add(text(t.name, 15f, if (on) Ui.ON_PRIMARY_LIGHT else Ui.TEXT, true), wrap())
        val details = listOf("${t.maxQuestions} questions", if (t.custom) "learned" else "built-in") + t.fields.map { it.name }.take(2)
        texts.add(text(details.joinToString(" · "), 12.5f, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, wrap())
        row.add(texts, weighted())
        if (t.custom) row.add(iconButton(Icon.TRASH, Ui.RED, sizeDp = 40) {
            confirm("Delete \"${t.name}\"?", "This learned sheet type and its answer keys will be removed.", "Delete", danger = true) {
                store.deleteTemplate(t.id)
                refresh()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        if (on) row.add(ImageView(this).apply { setImageDrawable(IconDrawable(Icon.CHECK, Ui.PRIMARY, dp(22))) }, wrap().apply { marginStart = dp(6) })
        c.add(row, block(0))
        c
    }
}
