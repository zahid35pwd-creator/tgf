package com.omrscanner.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.omrscanner.core.SheetLayout

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var examSummary: TextView
    private lateinit var keySummary: TextView
    private lateinit var historyButton: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "OMR Scanner"
        val page = scrollingPage()

        page.add(text("Scan an answer sheet to get the result.", 16f, Ui.MUTED))

        val exam = card()
        exam.add(text("Current exam", 13f, Ui.MUTED, bold = true), block(4))
        examSummary = exam.add(text("", 16f), block(4)) as TextView
        keySummary = exam.add(text("", 14f, Ui.MUTED), block(12)) as TextView
        val row = horizontal()
        row.add(button("Exam settings", ButtonStyle.SECONDARY) { editSettings() }, weighted().apply { marginEnd = dp(8) })
        row.add(button("Answer key", ButtonStyle.SECONDARY) {
            startActivity(Intent(this, AnswerKeyActivity::class.java))
        }, weighted())
        exam.add(row, block(0))
        page.add(exam, block(16))

        page.add(button("Scan sheet with camera") { ImageSources.openCamera(this) })
        page.add(button("Choose scanned picture") { ImageSources.openGallery(this) }, block(16))

        page.add(button("Print blank OMR sheet", ButtonStyle.SECONDARY) { SheetPrinter.print(this, store.settings.sheetSpec) })
        historyButton = page.add(button("Saved results", ButtonStyle.SECONDARY) {
            startActivity(Intent(this, HistoryActivity::class.java))
        }, block(20)) as android.widget.Button

        val tips = card()
        tips.add(text("Tips for a good scan", 15f, bold = true), block(6))
        tips.add(
            text(
                "• Print sheets from this app (any printer, A4).\n" +
                    "• Keep all 4 black corner squares in the picture.\n" +
                    "• Let the sheet fill most of the photo, lying flat.\n" +
                    "• Avoid strong shadows and glare.\n" +
                    "• You can also share a picture to this app from your gallery or scanner app.",
                14f, Ui.MUTED,
            ),
            block(0),
        )
        page.add(tips)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val s = store.settings
        val name = s.examName.ifBlank { "Untitled exam" }
        val negative = if (s.perWrong > 0) ", −${fmt(s.perWrong)} per wrong" else ""
        examSummary.text = "$name\n${s.questionCount} questions • options ${s.labelRange} • +${fmt(s.perCorrect)} per correct$negative"
        val keyed = store.key.count { it != null }
        keySummary.text = if (keyed == 0) "Answer key not set yet" else "Answer key: $keyed of ${s.questionCount} set"
        val saved = store.history().size
        historyButton.text = if (saved == 0) "Saved results" else "Saved results ($saved)"
    }

    private fun editSettings() {
        val s = store.settings
        val form = vertical(20)
        fun field(label: String, value: String, type: Int): EditText {
            form.add(text(label, 13f, Ui.MUTED), block(2))
            val e = EditText(this).apply { setText(value); inputType = type; setSingleLine() }
            form.add(e, block(10))
            return e
        }
        val name = field("Exam name (printed on the sheet)", s.examName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val count = field("Number of questions (1–${SheetLayout.MAX_QUESTIONS})", s.questionCount.toString(), InputType.TYPE_CLASS_NUMBER)

        form.add(text("Options per question", 13f, Ui.MUTED), block(2))
        val options = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        val four = RadioButton(this).apply { text = "4"; id = 4 }
        val five = RadioButton(this).apply { text = "5"; id = 5 }
        options.addView(four); options.addView(five)
        options.check(if (s.optionCount == 5) 5 else 4)
        form.add(options, block(10))

        form.add(text("Option letters", 13f, Ui.MUTED), block(2))
        val letters = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        val latin = RadioButton(this).apply { text = "A B C D"; id = 1 }
        val bangla = RadioButton(this).apply { text = "ক খ গ ঘ"; id = 2 }
        letters.addView(latin); letters.addView(bangla)
        letters.check(if (s.banglaLabels) 2 else 1)
        form.add(letters, block(10))

        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val correct = field("Marks per correct answer", fmt(s.perCorrect), decimal)
        val wrong = field("Negative marks per wrong answer (0 for none)", fmt(s.perWrong), decimal)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Exam settings")
            .setView(android.widget.ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = count.text.toString().toIntOrNull()
                val pc = correct.text.toString().toDoubleOrNull()
                val pw = wrong.text.toString().ifBlank { "0" }.toDoubleOrNull()
                when {
                    n == null || n !in 1..SheetLayout.MAX_QUESTIONS -> count.error = "Enter 1 to ${SheetLayout.MAX_QUESTIONS}"
                    pc == null || pc <= 0 -> correct.error = "Enter a positive number"
                    pw == null || pw < 0 -> wrong.error = "Enter 0 or more"
                    else -> {
                        store.settings = ExamSettings(
                            examName = name.text.toString().trim(),
                            questionCount = n,
                            optionCount = options.checkedRadioButtonId,
                            banglaLabels = letters.checkedRadioButtonId == 2,
                            perCorrect = pc,
                            perWrong = pw,
                        )
                        dialog.dismiss()
                        refresh()
                    }
                }
            }
        }
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = ImageSources.resultUri(this, requestCode, resultCode, data) ?: return
        startActivity(Intent(this, ScanActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/** 1.0 -> "1", 0.25 -> "0.25" (always ASCII digits, so the text can be parsed back). */
fun fmt(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
