package com.omrscanner.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.omrscanner.core.GradeResult
import com.omrscanner.core.Grader
import com.omrscanner.core.MarkStatus
import com.omrscanner.core.OmrException
import com.omrscanner.core.OmrReader
import com.omrscanner.core.OmrResult
import com.omrscanner.core.QuestionReading
import com.omrscanner.core.ReaderOptions
import com.omrscanner.core.Verdict

/**
 * Reads one picture of a sheet and shows the result. Started from the home screen, from another app's
 * "share"/"open with", or (with [EXTRA_KEY_MODE]) to read a filled-in sheet as the answer key.
 */
class ScanActivity : Activity() {
    private lateinit var store: Store
    private lateinit var settings: ExamSettings
    private var keyMode = false
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        settings = store.settings
        keyMode = intent.getBooleanExtra(EXTRA_KEY_MODE, false)
        title = if (keyMode) "Scan answer key" else "Result"

        val uri = ImageSources.sharedUri(intent)
        if (uri == null) {
            showError("No picture was received.")
            return
        }
        showProgress()
        process(uri)
    }

    private fun process(uri: Uri) {
        val options = ReaderOptions(questionCount = settings.questionCount, optionCount = settings.optionCount)
        Thread {
            var result: OmrResult? = null
            var image: Bitmap? = null
            var error: String? = null
            try {
                val picture = ImageSources.load(this, uri) ?: throw OmrException("Could not open this picture.")
                result = OmrReader.read(picture, options)
                image = ResultImage.render(result, if (keyMode) null else gradeOf(result))
            } catch (e: OmrException) {
                error = e.message
            } catch (e: OutOfMemoryError) {
                error = "Not enough memory to read this picture. Try a smaller photo."
            } catch (e: Exception) {
                error = "Could not read this picture (${e.javaClass.simpleName})."
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val r = result
                if (r == null) showError(error ?: "Unknown error") else showResult(r, image)
            }
        }.start()
    }

    private fun gradeOf(result: OmrResult): GradeResult? {
        val key = store.key
        if (key.all { it == null }) return null
        return Grader.grade(result.answers, key, settings.marking)
    }

    // ---- Screens -----------------------------------------------------------------------------

    private fun showProgress() {
        val box = vertical(32).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.BACKGROUND)
        }
        box.add(ProgressBar(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })
        box.add(text("Reading the sheet…", 17f, Ui.MUTED), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(box)
    }

    private fun showError(message: String) {
        val page = scrollingPage()
        val c = card()
        c.add(text("Couldn't read the sheet", 20f, Ui.RED, bold = true), block(8))
        c.add(text(message, 16f), block(12))
        c.add(
            text(
                "Make sure the picture shows a sheet printed from this app, with all 4 black corner squares visible, " +
                    "the sheet filling most of the frame, and no heavy shadow.",
                14f, Ui.MUTED,
            ),
            block(0),
        )
        page.add(c, block(16))
        page.add(button("Take another photo") { ImageSources.openCamera(this) })
        page.add(button("Choose another picture", ButtonStyle.SECONDARY) { ImageSources.openGallery(this) })
    }

    private fun showResult(result: OmrResult, image: Bitmap?) {
        val page = scrollingPage()
        val grade = if (keyMode) null else gradeOf(result)
        when {
            keyMode -> keySummary(page, result)
            grade != null -> scoreSummary(page, result, grade)
            else -> noKeySummary(page, result)
        }

        if (image != null) {
            val holder = card().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
            val view = ImageView(this).apply {
                setImageBitmap(image)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            holder.add(view, block(0))
            page.add(holder, block(8))
            if (grade != null) page.add(text(legend(), 13f, Ui.MUTED), block(16))
        }

        page.add(questionTable(result, grade), block(16))

        if (!keyMode) {
            page.add(button("Scan next sheet") { ImageSources.openCamera(this) })
            page.add(button("Choose next picture", ButtonStyle.SECONDARY) { ImageSources.openGallery(this) })
        }
    }

    private fun scoreSummary(page: LinearLayout, result: OmrResult, grade: GradeResult) {
        val c = card()
        c.add(text(settings.examName.ifBlank { "Score" }, 14f, Ui.MUTED, bold = true), block(4))
        val scoreRow = horizontal()
        scoreRow.add(text("${fmt(grade.score)} / ${fmt(grade.maxScore)}", 34f, Ui.PRIMARY, bold = true), weighted())
        scoreRow.add(text(String.format(java.util.Locale.US, "%.1f%%", grade.percent), 22f, Ui.TEXT, bold = true))
        c.add(scoreRow, block(12))

        val counts = horizontal()
        counts.add(stat("Correct", grade.correct, Ui.GREEN), weighted())
        counts.add(stat("Wrong", grade.wrong, Ui.RED), weighted())
        counts.add(stat("Blank", grade.blank, Ui.ORANGE), weighted())
        counts.add(stat("Multiple", grade.multiple, Ui.PURPLE), weighted())
        c.add(counts, block(12))

        if (grade.reviewCount > 0) {
            c.add(text("${grade.reviewCount} question(s) had faint or erased marks — check them on the image.", 13f, Ui.ORANGE), block(12))
        }

        c.add(text("Roll number", 13f, Ui.MUTED), block(2))
        val roll = EditText(this).apply {
            setText(result.roll)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            textSize = 20f
        }
        c.add(roll, block(4))
        if (result.rollDigits.any { it == null }) c.add(text("Some roll digits were not filled clearly (shown as ?). You can correct them above.", 13f, Ui.ORANGE), block(8))

        val save = button("Save result") {}
        save.setOnClickListener {
            if (saved) return@setOnClickListener
            store.addResult(
                SavedResult(
                    time = System.currentTimeMillis(), exam = settings.examName, roll = roll.text.toString().trim(),
                    score = grade.score, maxScore = grade.maxScore, correct = grade.correct, wrong = grade.wrong,
                    blank = grade.blank, multiple = grade.multiple, answers = answerString(result),
                ),
            )
            saved = true
            save.text = "Saved ✓"
            save.isEnabled = false
            toast("Result saved")
        }
        c.add(save, block(0))
        page.add(c, block(12))
    }

    private fun noKeySummary(page: LinearLayout, result: OmrResult) {
        val c = card()
        c.add(text("No answer key yet", 20f, bold = true), block(6))
        c.add(text("Showing the detected answers only. Set the answer key to get scores — you can use this sheet as the key.", 14f, Ui.MUTED), block(8))
        c.add(text("Roll number: ${result.roll}", 16f), block(12))
        c.add(button("Use this sheet as the answer key", ButtonStyle.SECONDARY) { saveAsKey(result) }, block(0))
        page.add(c, block(12))
    }

    private fun keySummary(page: LinearLayout, result: OmrResult) {
        val c = card()
        val filled = result.answers.count { it.status == MarkStatus.SINGLE }
        val problems = result.answers.count { it.status != MarkStatus.SINGLE }
        c.add(text("Answer key detected", 20f, bold = true), block(6))
        c.add(text("$filled of ${result.answers.size} questions have one answer marked.", 15f), block(4))
        if (problems > 0) c.add(text("$problems question(s) are blank or have several marks; they will not be graded. You can fix them in the answer key screen.", 14f, Ui.ORANGE), block(8))
        c.add(button("Save as answer key") { saveAsKey(result) }, block(0))
        page.add(c, block(12))
    }

    private fun saveAsKey(result: OmrResult) {
        store.key = result.answers.map { it.answer }
        toast("Answer key saved")
        if (keyMode) {
            setResult(RESULT_OK)
            finish()
        } else {
            // Show this same sheet graded against its own key (a useful sanity check).
            showResult(result, ResultImage.render(result, gradeOf(result)))
        }
    }

    private fun stat(label: String, n: Int, color: Int) = vertical().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        add(text(n.toString(), 22f, color, bold = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(text(label, 12f, Ui.MUTED), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun legend() = "Green: correct answer • Red: wrong mark • Orange: unanswered • Purple: several marks"

    private fun marks(a: QuestionReading) = when (a.status) {
        MarkStatus.BLANK -> "—"
        else -> a.marked.sorted().joinToString(",") { settings.label(it) }
    }

    private fun questionTable(result: OmrResult, grade: GradeResult?): LinearLayout {
        val c = card().apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        c.add(text("Answers", 16f, bold = true), block(8))
        val header = horizontal()
        header.add(text("Q", 13f, Ui.MUTED, bold = true), weighted(0.7f))
        header.add(text("Marked", 13f, Ui.MUTED, bold = true), weighted(1.2f))
        if (grade != null) {
            header.add(text("Key", 13f, Ui.MUTED, bold = true), weighted(1f))
            header.add(text("Result", 13f, Ui.MUTED, bold = true), weighted(1.3f))
        }
        c.add(header, block(4))
        for (a in result.answers) {
            val g = grade?.questions?.getOrNull(a.question)
            val row = horizontal().apply {
                setPadding(dp(4), dp(6), dp(4), dp(6))
                if (a.question % 2 == 1) setBackgroundColor(Ui.BACKGROUND)
            }
            row.add(text("${a.question + 1}", 15f, Ui.MUTED), weighted(0.7f))
            row.add(text(marks(a) + if (a.needsReview) "  ⚠" else "", 15f, bold = true), weighted(1.2f))
            if (g != null) {
                row.add(text(g.key?.let { settings.label(it) } ?: "—", 15f), weighted(1f))
                val (label, color) = when (g.verdict) {
                    Verdict.CORRECT -> "Correct" to Ui.GREEN
                    Verdict.WRONG -> "Wrong" to Ui.RED
                    Verdict.BLANK -> "Blank" to Ui.ORANGE
                    Verdict.MULTIPLE -> "Multiple" to Ui.PURPLE
                    Verdict.UNKEYED -> "No key" to Ui.MUTED
                }
                row.add(text(label, 15f, color, bold = true), weighted(1.3f))
            }
            c.add(row, block(0))
        }
        return c
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = ImageSources.resultUri(this, requestCode, resultCode, data) ?: return
        startActivity(
            Intent(this, ScanActivity::class.java).setData(uri)
                .putExtra(EXTRA_KEY_MODE, keyMode)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        finish()
    }

    companion object {
        const val EXTRA_KEY_MODE = "keyMode"

        /** One character per question for saved results: A–E, '-' blank, '*' several marks. */
        fun answerString(result: OmrResult) = result.answers.joinToString("") { a ->
            when (a.status) {
                MarkStatus.BLANK -> "-"
                MarkStatus.MULTIPLE -> "*"
                MarkStatus.SINGLE -> ('A' + a.marked[0]).toString()
            }
        }
    }
}
