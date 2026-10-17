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

/** Home: the active exam, choosing the OMR sheet, sheet tools and tips. */
class HomeTab(private val a: MainActivity) {
    private val s = a.store.settings

    private fun save(new: ExamSettings) {
        a.store.settings = new
        a.refresh()
    }

    fun build(): View = with(a) {
        val (root, column) = tabPage("OMR Scanner", iconButton(Icon.MORE) { appMenu() })

        column.add(sectionHeader("Active exam"), block(4))
        column.add(examCard(), block(18))
        column.add(guide(), block(18))

        column.add(sectionHeader("OMR sheet"), block(4))
        column.add(banner(Icon.INFO,
            "Choose a ready-made sheet only if your answer sheet is exactly the same design. For any other OMR sheet, add it as a new sheet first.",
            Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT), block(10))
        for (t in store.templates()) column.add(templateCard(t), block(10))
        column.add(addSheetCard(), block(18))

        column.add(sectionHeader("Download blank sheets"), block(4))
        val tools = card(6)
        val blanks = listOf(
            Triple("100 MCQ OMR sheet", 100, 4),
            Triple("100 MCQ OMR sheet (5 options)", 100, 5),
            Triple("50 MCQ OMR sheet", 50, 4),
        )
        blanks.forEachIndexed { i, (name, q, opt) ->
            if (i > 0) tools.add(divider(), dividerParams())
            tools.add(listRow(Icon.SAVE, name, "$q questions · $opt options · PDF") {
                SheetPrinter.sharePdf(this, com.omrscanner.core.SheetSpec(q, opt,
                    if (s.banglaLabels) com.omrscanner.core.SheetSpec.BANGLA_LABELS else com.omrscanner.core.SheetSpec.LATIN_LABELS,
                    examName = s.examName))
            }, block(0))
        }
        column.add(tools, block(6))
        column.add(text("Board sheets (BCS, SSC/HSC) use the official printed sheets.", 12.5f, Ui.MUTED).apply {
            setPadding(dp(4), 0, dp(4), 0)
        }, block(18))

        root
    }

    /** Step-by-step guide with ticks, so a first-time user knows what to do next. */
    private fun guide(): View = with(a) {
        val keyed = store.storedKey(null).any { it != null } || store.setsWithKeys().isNotEmpty()
        val steps = listOf(
            Triple("Choose the OMR sheet", "Below: a ready-made sheet if yours is exactly the same, otherwise add yours", store.hasTemplate) to { },
            Triple("Set the answer key", "Key tab: scan a filled key (camera, photo, PDF) or type it; set marking", keyed) to { show(MainActivity.Tab.KEY) },
            Triple("Scan the students' sheets", "Scanner tab: camera, photo or PDF — tap Save on each result", store.history().isNotEmpty()) to { show(MainActivity.Tab.SCANNER) },
            Triple("See and export results", "Results tab: exam-wise results, Word / Excel / PDF", store.history().isNotEmpty()) to { show(MainActivity.Tab.RESULTS) },
        )
        val next = steps.indexOfFirst { !it.first.third }
        val box = vertical()
        box.add(sectionHeader(if (next < 0) "Tips" else "Getting started  ·  step ${next + 1} of 4"), block(4))
        val c = card(6)
        steps.forEachIndexed { i, (st, go) ->
            val (title, sub, done) = st
            if (i > 0) c.add(divider(), dividerParams())
            val row = horizontal().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = ripple(rounded(android.graphics.Color.TRANSPARENT, dp(16).toFloat()), dp(16).toFloat())
                setOnClickListener { go() }
            }
            val badge = if (done) iconBadge(Icon.CHECK, Ui.ON_PRIMARY, Ui.GREEN, 36, 18)
            else text("${i + 1}", 15f, if (i == next) Ui.ON_PRIMARY else Ui.MUTED, true).apply {
                gravity = Gravity.CENTER
                background = rounded(if (i == next) Ui.PRIMARY else Ui.CARD_ALT, dp(18).toFloat())
            }
            row.add(badge, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(14) })
            val t = vertical()
            t.add(text(title, 15f, if (done) Ui.MUTED else Ui.TEXT, true), wrap())
            t.add(text(sub, 12.5f, Ui.MUTED), wrap())
            row.add(t, weighted())
            c.add(row, block(0))
        }
        box.add(c, block(0))
        box
    }

    private fun dividerParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1)).apply { marginStart = a.dp(68) }

    private fun banner(icon: Icon, message: String, fg: Int, bg: Int): View = with(a) {
        val row = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(bg, dp(16).toFloat()) }
        row.add(iconBadge(icon, fg, android.graphics.Color.TRANSPARENT, 28, 20), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        row.add(text(message, 13.5f, fg), weighted())
        row
    }

    // ---- Active exam -------------------------------------------------------------------------

    private fun examCard(): View = with(a) {
        val c = clickableCard(18) { editExam() }
        val top = horizontal()
        val info = vertical()
        info.add(text(s.examName.ifBlank { "Untitled exam" }, TextStyle.HEADING).apply { maxLines = 2 }, block(2))
        info.add(text(if (store.hasTemplate) s.template.name else "No OMR sheet selected", TextStyle.CAPTION,
            if (store.hasTemplate) Ui.MUTED else Ui.ORANGE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, block(0))
        top.add(info, weighted())
        val editChip = horizontal().apply {
            setPadding(dp(10), dp(8), dp(12), dp(8))
            background = rounded(Ui.PRIMARY_LIGHT, dp(14).toFloat())
        }
        editChip.add(iconBadge(Icon.EDIT, Ui.ON_PRIMARY_LIGHT, android.graphics.Color.TRANSPARENT, 20, 16),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(6) })
        editChip.add(text("Edit", 13.5f, Ui.ON_PRIMARY_LIGHT, true), wrap())
        top.add(editChip, wrap().apply { marginStart = dp(10) })
        c.add(top, block(14))

        val chips = horizontal()
        chips.add(chip("${s.questionCount} questions"), wrap().apply { marginEnd = dp(6) })
        chips.add(chip("${s.optionCount} options · ${s.labelRange}"), wrap().apply { marginEnd = dp(6) })
        val marks = "+${fmt(s.perCorrect)}" + if (s.perWrong > 0) " / −${fmt(s.perWrong)}" else ""
        chips.add(chip(marks, Ui.GREEN, Ui.GREEN_BG), wrap())
        c.add(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, block(12))

        val keyed = store.storedKey(null).count { it != null } + store.setsWithKeys().size
        c.add(text(if (keyed == 0) "Answer key not set yet — open the Key tab" else "Answer key ready", 13f,
            if (keyed == 0) Ui.ORANGE else Ui.GREEN, true), block(0))
        c
    }

    /** One sheet with every exam setting: name, questions, options (4 or 5), letters and marking. */
    private fun editExam() = a.bottomSheet("Exam settings") { col, d ->
        with(a) {
            val t = s.template
            val (nameBox, name) = inputField("Exam name (printed on the sheet and reports)", s.examName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            col.add(nameBox, block(12))
            val (countBox, count) = inputField("Number of questions (1–${t.maxQuestions})", s.questionCount.toString(), InputType.TYPE_CLASS_NUMBER)
            col.add(countBox, block(12))

            val optionChoices = listOf(4, 5).filter { it <= t.maxOptions }.ifEmpty { listOf(t.maxOptions) }
            var options = s.optionCount.coerceIn(optionChoices.first(), optionChoices.last())
            col.add(text("Options per question", TextStyle.CAPTION, Ui.MUTED).apply { setPadding(dp(4), 0, 0, dp(6)) }, wrap())
            lateinit var optBar: View
            fun optionBar(): View = segmented(optionChoices.map { "$it options" }, optionChoices.indexOf(options).coerceAtLeast(0)) { i ->
                options = optionChoices[i]
                val idx = col.indexOfChild(optBar)
                col.removeView(optBar)
                optBar = optionBar()
                col.addView(optBar, idx, block(12))
            }
            optBar = optionBar()
            col.add(optBar, block(12))

            var bangla = s.banglaLabels
            col.add(text("Option letters", TextStyle.CAPTION, Ui.MUTED).apply { setPadding(dp(4), 0, 0, dp(6)) }, wrap())
            lateinit var letterBar: View
            fun lettersBar(): View = segmented(listOf("A B C D", "ক খ গ ঘ"), if (bangla) 1 else 0) { i ->
                bangla = i == 1
                val idx = col.indexOfChild(letterBar)
                col.removeView(letterBar)
                letterBar = lettersBar()
                col.addView(letterBar, idx, block(12))
            }
            letterBar = lettersBar()
            col.add(letterBar, block(12))

            val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val marks = horizontal()
            val (cBox, correct) = inputField("Marks per correct", fmt(s.perCorrect), decimal)
            val (wBox, wrong) = inputField("Negative per wrong", fmt(s.perWrong), decimal)
            marks.add(cBox, weighted().apply { marginEnd = dp(10) })
            marks.add(wBox, weighted())
            col.add(marks, block(18))

            col.add(button("Save") {
                val n = count.text.toString().toIntOrNull()
                val pc = correct.text.toString().toDoubleOrNull()
                val pw = wrong.text.toString().ifBlank { "0" }.toDoubleOrNull()
                when {
                    n == null || n !in 1..t.maxQuestions -> count.error = "Enter 1 to ${t.maxQuestions}"
                    pc == null || pc <= 0 -> correct.error = "Enter a positive number"
                    pw == null || pw < 0 -> wrong.error = "Enter 0 or more"
                    else -> {
                        d.dismiss()
                        save(s.copy(examName = name.text.toString().trim(), questionCount = n, optionCount = options,
                            banglaLabels = bangla, perCorrect = pc, perWrong = pw))
                    }
                }
            }, block(0))
        }
    }

    // ---- OMR sheets --------------------------------------------------------------------------

    private fun templateCard(t: SheetTemplate): View = with(a) {
        val on = store.hasTemplate && t.id == s.templateId
        val r = dp(20).toFloat()
        val c = clickableCard(14) {
            if (on) return@clickableCard
            save(s.copy(
                template = t,
                questionCount = t.maxQuestions,
                optionCount = s.optionCount.coerceIn(minOf(4, t.maxOptions), t.maxOptions),
                // Board sheets print ক খ গ ঘ; default to them when switching to one.
                banglaLabels = t.optionLabels == SheetSpec.BANGLA_LABELS,
            ))
            snackbar("Selected \"${t.name}\"")
        }
        c.background = ripple(rounded(if (on) Ui.PRIMARY_LIGHT else Ui.CARD, r, if (on) Ui.PRIMARY else Ui.DIVIDER, dp(if (on) 1.5f else 1f)), r)
        val row = horizontal().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val thumbW = dp(44)
        val thumbH = (thumbW * t.pageHeight / t.pageWidth).toInt()
        val thumb = ImageView(this).apply {
            setImageBitmap(TemplateThumbnail.get(t, thumbW))
            background = rounded(Ui.CARD_ALT, dp(6).toFloat(), if (on) Ui.PRIMARY else Ui.DIVIDER, dp(1f))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.add(thumb, LinearLayout.LayoutParams(thumbW, thumbH).apply { marginEnd = dp(14) })
        val texts = vertical()
        texts.add(text(t.name, 15f, Ui.TEXT, true), wrap())
        val details = listOf("${t.maxQuestions} questions", if (t.custom) "added by you" else "ready-made") + t.fields.map { it.name }.take(2)
        texts.add(text(details.joinToString(" · "), 12.5f, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, wrap())
        row.add(texts, weighted())
        if (t.custom) row.add(iconButton(Icon.TRASH, Ui.RED, sizeDp = 40) {
            confirm("Delete \"${t.name}\"?", "This sheet and its answer keys will be removed.", "Delete", danger = true) {
                store.deleteTemplate(t.id)
                refresh()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.add(ImageView(this).apply {
            setImageDrawable(IconDrawable(if (on) Icon.CHECK else Icon.CHEVRON, if (on) Ui.PRIMARY else Ui.MUTED, dp(22)))
        }, wrap().apply { marginStart = dp(6) })
        c.add(row, block(0))
        c
    }

    private fun addSheetCard(): View = with(a) {
        val c = clickableCard(16) { startActivity(Intent(this, SheetSetupActivity::class.java)) }
        c.background = ripple(rounded(Ui.CARD, dp(20).toFloat(), Ui.PRIMARY, dp(1.5f)), dp(20).toFloat())
        val row = horizontal()
        row.add(iconBadge(Icon.PLUS, Ui.ON_PRIMARY, Ui.PRIMARY, 44, 22), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text("Add a new OMR sheet", 15f, Ui.TEXT, true), wrap())
        t.add(text("For any other design: give the app a blank copy (PDF, scan or photo)", 12.5f, Ui.MUTED), wrap())
        row.add(t, weighted())
        c.add(row, block(0))
        c
    }

    companion object {
        /** Top-of-tab banner naming the selected OMR sheet, or asking to pick one on Home. Null view text when fine. */
        fun selectedSheet(a: MainActivity): View = with(a) {
            val row = horizontal().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Ui.CARD, dp(14).toFloat(), if (store.hasTemplate) Ui.DIVIDER else Ui.ORANGE, dp(1))
            }
            if (store.hasTemplate) {
                row.add(iconBadge(Icon.SHEET, Ui.ON_PRIMARY_LIGHT, Ui.PRIMARY_LIGHT, 36, 18), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                val t = vertical()
                t.add(text("OMR SHEET", 11f, Ui.MUTED, true).apply { letterSpacing = 0.08f }, wrap())
                t.add(text(store.settings.template.name, 15f, Ui.TEXT, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, wrap())
                row.add(t, weighted())
                row.add(text("Change", 13.5f, Ui.PRIMARY, true).apply {
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { show(MainActivity.Tab.HOME) }
                }, wrap())
            } else {
                row.add(iconBadge(Icon.WARNING, Ui.ORANGE, Ui.ORANGE_BG, 36, 18), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                row.add(text("No OMR sheet selected. Choose one on the Home tab first.", 14f, Ui.TEXT), weighted())
                row.add(text("Home", 13.5f, Ui.PRIMARY, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { show(MainActivity.Tab.HOME) }
                }, wrap())
            }
            row
        }
    }
}
